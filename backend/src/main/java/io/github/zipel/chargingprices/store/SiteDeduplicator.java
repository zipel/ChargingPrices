package io.github.zipel.chargingprices.store;

import io.github.zipel.chargingprices.domain.ChargePoint;
import io.github.zipel.chargingprices.domain.Site;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Merges the sites of several feeds into one list without duplicates.
 *
 * <p>The same charger can appear in two feeds. Shell's stations, for example, are in Spirii's feed and in
 * Eco-Movement's, with different IDs. Feeds earlier in the list win. A later site is dropped when it shares
 * an EVSE ID with an earlier feed's site, or when an earlier feed has a site with the same name within
 * {@value #SAME_PLACE_METRES} metres. Sites within one feed are never merged with each other.
 */
final class SiteDeduplicator {

    static final double SAME_PLACE_METRES = 50;

    /** About 110 m north–south and 60 m east–west in Denmark, so neighbouring cells cover 50 m. */
    private static final double CELL_DEGREES = 0.001;
    private static final double EARTH_RADIUS_METRES = 6_371_000;

    private SiteDeduplicator() {
    }

    static List<Site> merge(List<List<Site>> feedsInPriorityOrder) {
        List<Site> kept = new ArrayList<>();
        Set<String> earlierEvseIds = new HashSet<>();
        Map<Long, List<Site>> earlierSitesByCell = new HashMap<>();

        for (List<Site> feed : feedsInPriorityOrder) {
            List<Site> keptFromFeed = new ArrayList<>();
            for (Site site : feed) {
                if (!sharesEvseId(site, earlierEvseIds) && !hasNamesakeNearby(site, earlierSitesByCell)) {
                    keptFromFeed.add(site);
                }
            }
            for (Site site : keptFromFeed) {
                earlierSitesByCell.computeIfAbsent(cell(site.latitude(), site.longitude()), k -> new ArrayList<>()).add(site);
                for (ChargePoint point : site.chargePoints()) {
                    String id = normalise(point.evseId());
                    if (id != null) {
                        earlierEvseIds.add(id);
                    }
                }
            }
            kept.addAll(keptFromFeed);
        }
        return kept;
    }

    private static boolean sharesEvseId(Site site, Set<String> earlierEvseIds) {
        return site.chargePoints().stream()
                .map(point -> normalise(point.evseId()))
                .anyMatch(id -> id != null && earlierEvseIds.contains(id));
    }

    private static boolean hasNamesakeNearby(Site site, Map<Long, List<Site>> earlierSitesByCell) {
        String name = normalise(site.name());
        if (name == null) {
            return false;
        }
        long latCell = Math.round(site.latitude() / CELL_DEGREES);
        long lonCell = Math.round(site.longitude() / CELL_DEGREES);
        for (long dLat = -1; dLat <= 1; dLat++) {
            for (long dLon = -1; dLon <= 1; dLon++) {
                for (Site other : earlierSitesByCell.getOrDefault(key(latCell + dLat, lonCell + dLon), List.of())) {
                    if (name.equals(normalise(other.name()))
                            && metres(site.latitude(), site.longitude(), other.latitude(), other.longitude()) <= SAME_PLACE_METRES) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Upper case letters and digits only, so "DK*SPI*E1" and "DK.SPI.E1", or "Shell Express Nibe" and
     * "shell express nibe", compare equal.
     */
    static String normalise(String text) {
        if (text == null) {
            return null;
        }
        String normalised = text.toUpperCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
        return normalised.isEmpty() ? null : normalised;
    }

    private static long cell(double latitude, double longitude) {
        return key(Math.round(latitude / CELL_DEGREES), Math.round(longitude / CELL_DEGREES));
    }

    private static long key(long latCell, long lonCell) {
        return (latCell << 32) ^ (lonCell & 0xffffffffL);
    }

    /** Distance along the earth's surface (haversine formula). */
    static double metres(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_METRES * Math.asin(Math.sqrt(h));
    }
}
