package io.github.zipel.chargingprices.store;

import io.github.zipel.chargingprices.domain.ChargePoint;
import io.github.zipel.chargingprices.domain.Site;
import io.github.zipel.chargingprices.feed.datex.DatexParser.StatusUpdate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Holds the latest snapshot of every site in memory.
 *
 * <p>The data is a live copy of the feeds and is reloaded within minutes after a restart, so it doesn't need
 * a database yet (see docs/decisions/0003-in-memory-snapshot.md). Writes replace whole immutable lists, so
 * readers never see a half-updated state and don't need locks.
 */
@Component
public class SiteStore {

    /** Sites grouped by the feed part they came from, e.g. "spirii/7" for one operator in Spirii's feed. */
    private final Map<String, List<Site>> sitesByPart = new LinkedHashMap<>();

    private volatile List<Site> all = List.of();
    private volatile Map<String, Site> byId = Map.of();

    public List<Site> all() {
        return all;
    }

    public Optional<Site> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public synchronized boolean hasPart(String part) {
        return sitesByPart.containsKey(part);
    }

    /** Replaces the static data of one feed part, keeping the last known status of charge points that still exist. */
    public synchronized void replacePart(String part, List<Site> sites) {
        Map<String, ChargePoint> previous = new HashMap<>();
        for (Site site : sitesByPart.getOrDefault(part, List.of())) {
            for (ChargePoint point : site.chargePoints()) {
                previous.put(point.id(), point);
            }
        }
        List<Site> merged = sites.stream()
                .map(site -> site.withChargePoints(site.chargePoints().stream()
                        .map(point -> {
                            ChargePoint old = previous.get(point.id());
                            return old == null ? point : point.withStatus(old.availability(), old.tariff(), old.statusUpdated());
                        })
                        .toList()))
                .toList();
        sitesByPart.put(part, merged);
        rebuild();
    }

    /** Applies status updates (availability and prices) to the charge points of one feed part. */
    public synchronized void applyStatuses(String part, Map<String, StatusUpdate> updates) {
        List<Site> sites = sitesByPart.get(part);
        if (sites == null || updates.isEmpty()) {
            return;
        }
        List<Site> updated = sites.stream()
                .map(site -> site.withChargePoints(site.chargePoints().stream()
                        .map(point -> {
                            StatusUpdate update = updates.get(point.id());
                            return update == null ? point : point.withStatus(update.availability(), update.tariff(), update.updated());
                        })
                        .toList()))
                .toList();
        sitesByPart.put(part, updated);
        rebuild();
    }

    private void rebuild() {
        List<Site> allSites = new ArrayList<>();
        sitesByPart.values().forEach(allSites::addAll);
        Map<String, Site> index = new HashMap<>();
        for (Site site : allSites) {
            index.put(site.id(), site);
        }
        all = List.copyOf(allSites);
        byId = Map.copyOf(index);
    }
}
