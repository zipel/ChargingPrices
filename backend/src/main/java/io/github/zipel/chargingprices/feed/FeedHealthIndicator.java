package io.github.zipel.chargingprices.feed;

import io.github.zipel.chargingprices.store.SiteStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Adds the data's freshness to {@code /actuator/health}, so a host can restart the app when a feed stops
 * updating. A feed that is still loading counts as healthy; one whose last successful refresh is older than
 * {@code max-age}, or that has never succeeded, counts as down.
 */
@Component("feeds")
public class FeedHealthIndicator implements HealthIndicator {

    private final FeedStatus feedStatus;
    private final SiteStore store;
    private final Clock clock;
    private final Duration maxAge;

    public FeedHealthIndicator(FeedStatus feedStatus, SiteStore store, Clock clock,
            @Value("${feeds.max-age:PT25M}") Duration maxAge) {
        this.feedStatus = feedStatus;
        this.store = store;
        this.clock = clock;
        this.maxAge = maxAge;
    }

    @Override
    public Health health() {
        boolean down = false;
        Map<String, Object> details = new LinkedHashMap<>();
        for (FeedStatus.Source source : feedStatus.sources()) {
            var refresh = feedStatus.refresh(source.key()).orElse(null);
            if (refresh == null) {
                details.put(source.key(), "loading");
                continue;
            }
            boolean fresh = refresh.lastSuccess() != null
                    && Duration.between(refresh.lastSuccess(), Instant.now(clock)).compareTo(maxAge) <= 0;
            down |= !fresh;
            details.put(source.key(), Map.of(
                    "status", fresh ? "UP" : "DOWN",
                    "lastSuccess", refresh.lastSuccess() == null ? "never" : refresh.lastSuccess().toString(),
                    "failures", refresh.failures()));
        }
        details.put("sites", store.all().size());
        return (down ? Health.down() : Health.up()).withDetails(details).build();
    }
}
