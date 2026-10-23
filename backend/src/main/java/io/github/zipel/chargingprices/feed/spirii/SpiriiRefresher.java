package io.github.zipel.chargingprices.feed.spirii;

import io.github.zipel.chargingprices.domain.Site;
import io.github.zipel.chargingprices.feed.FeedStatus;
import io.github.zipel.chargingprices.feed.datex.DatexParser;
import io.github.zipel.chargingprices.store.SiteStore;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the store in sync with Spirii's feed.
 *
 * <p>Every run fetches the statuses (availability and prices) of each operator, and the static tables when
 * they are missing or older than {@code tables-refresh}. Requests are spaced out so the feed isn't hammered.
 */
@Component
@ConditionalOnBooleanProperty("feeds.spirii.enabled")
public class SpiriiRefresher {

    private static final Logger log = LoggerFactory.getLogger(SpiriiRefresher.class);
    static final String SOURCE = "spirii";

    /** Sites the operators have left in the feed for testing, such as "Nannasgade Test". */
    private static final Pattern TEST_SITE = Pattern.compile("(?i)\\btest\\b");

    private final SpiriiClient client;
    private final DatexParser parser;
    private final SiteStore store;
    private final SpiriiProperties properties;
    private final FeedStatus feedStatus;
    private final Clock clock;

    private final Map<String, String> etags = new HashMap<>();
    private final Map<String, Instant> tablesFetchedAt = new HashMap<>();

    public SpiriiRefresher(SpiriiClient client, DatexParser parser, SiteStore store, SpiriiProperties properties,
            FeedStatus feedStatus, Clock clock) {
        this.client = client;
        this.parser = parser;
        this.store = store;
        this.properties = properties;
        this.feedStatus = feedStatus;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${feeds.spirii.statuses-refresh}")
    public void refresh() {
        int failures = 0;
        for (int cpoId : properties.cpoIds()) {
            try {
                refreshOperator(cpoId);
            } catch (RuntimeException e) {
                failures++;
                log.warn("Spirii operator {} failed: {}", cpoId, e.getMessage());
            }
        }
        feedStatus.markRefreshed(SOURCE, Instant.now(clock), failures);
        log.info("Spirii refresh done: {} sites in store, {} operator(s) failed", store.all().size(), failures);
    }

    private void refreshOperator(int cpoId) {
        String part = SOURCE + "/" + cpoId;
        Instant fetchedAt = tablesFetchedAt.get(part);
        boolean tablesDue = fetchedAt == null || fetchedAt.plus(properties.tablesRefresh()).isBefore(Instant.now(clock));
        if (tablesDue || !store.hasPart(part)) {
            SpiriiClient.Response tables = client.fetch(SpiriiClient.TABLES, cpoId, etags.get(part + "/tables"));
            if (!tables.notModified()) {
                List<Site> sites = parser.parseTable(tables.body(), SOURCE).stream()
                        .filter(site -> site.name() == null || !TEST_SITE.matcher(site.name()).find())
                        .toList();
                store.replacePart(part, sites);
            }
            etags.put(part + "/tables", tables.etag());
            tablesFetchedAt.put(part, Instant.now(clock));
            pause();
        }

        SpiriiClient.Response statuses = client.fetch(SpiriiClient.STATUSES, cpoId, etags.get(part + "/statuses"));
        if (!statuses.notModified()) {
            store.applyStatuses(part, parser.parseStatuses(statuses.body()));
        }
        etags.put(part + "/statuses", statuses.etag());
        pause();
    }

    private void pause() {
        try {
            Thread.sleep(properties.pauseBetweenRequests());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
