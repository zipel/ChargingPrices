package io.github.zipel.chargingprices.feed.ecomovement;

import io.github.zipel.chargingprices.domain.Site;
import io.github.zipel.chargingprices.feed.FeedStatus;
import io.github.zipel.chargingprices.feed.datex.DatexParser;
import io.github.zipel.chargingprices.feed.datex.DatexParser.StatusUpdate;
import io.github.zipel.chargingprices.store.SiteStore;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the store in sync with Eco-Movement's feed: Clever, Circle K, Q8, Allego, Lidl, IONITY and others.
 *
 * <p>Switched on only when a token is set. Every run fetches the whole country, sites and statuses together,
 * and replaces the previous copy. If a run fails, the previous copy stays in use.
 */
@Component
@ConditionalOnExpression("!'${feeds.eco-movement.token:}'.isBlank()")
public class EcoMovementRefresher {

    private static final Logger log = LoggerFactory.getLogger(EcoMovementRefresher.class);
    static final String SOURCE = "ecomovement";

    private final EcoMovementClient client;
    private final DatexParser parser;
    private final SiteStore store;
    private final FeedStatus feedStatus;
    private final Clock clock;

    private volatile boolean shuttingDown;

    public EcoMovementRefresher(EcoMovementClient client, DatexParser parser, SiteStore store, FeedStatus feedStatus, Clock clock) {
        this.client = client;
        this.parser = parser;
        this.store = store;
        this.feedStatus = feedStatus;
        this.clock = clock;
        feedStatus.register(new FeedStatus.Source(SOURCE, "Eco-Movement AFIR feed (DATEX II)",
                "https://du-portal-ui.dataudveksler.app.vd.dk/data/1170/overview",
                "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"));
    }

    @EventListener(ContextClosedEvent.class)
    void stopRefreshing() {
        shuttingDown = true;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${feeds.eco-movement.refresh}")
    public void refresh() {
        if (shuttingDown) {
            return;
        }
        List<Site> sites = new ArrayList<>();
        Map<String, StatusUpdate> statuses = new HashMap<>();
        try {
            int pages = client.fetchAllPages(page -> {
                sites.addAll(parser.parseTable(page, SOURCE));
                statuses.putAll(parser.parseStatuses(page));
            });
            store.replacePart(SOURCE, sites);
            store.applyStatuses(SOURCE, statuses);
            feedStatus.markSucceeded(SOURCE, Instant.now(clock), 0);
            log.info("Eco-Movement refresh done: {} sites from {} pages", sites.size(), pages);
        } catch (RuntimeException e) {
            feedStatus.markFailed(SOURCE, Instant.now(clock));
            log.warn("Eco-Movement refresh failed, keeping the previous data: {}", e.getMessage());
        }
    }
}
