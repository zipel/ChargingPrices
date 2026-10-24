package io.github.zipel.chargingprices.feed;

import io.github.zipel.chargingprices.store.SiteStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Adds the data's freshness to {@code /actuator/health}, so a host can restart the app when the feed stops
 * updating. Still loading counts as healthy; data older than {@code max-age} counts as down.
 */
@Component("feeds")
public class FeedHealthIndicator implements HealthIndicator {

    private final FeedStatus feedStatus;
    private final SiteStore store;
    private final Clock clock;
    private final boolean enabled;
    private final Duration maxAge;

    public FeedHealthIndicator(FeedStatus feedStatus, SiteStore store, Clock clock,
            @Value("${feeds.spirii.enabled}") boolean enabled,
            @Value("${feeds.max-age:PT10M}") Duration maxAge) {
        this.feedStatus = feedStatus;
        this.store = store;
        this.clock = clock;
        this.enabled = enabled;
        this.maxAge = maxAge;
    }

    @Override
    public Health health() {
        if (!enabled) {
            return Health.up().withDetail("spirii", "disabled").build();
        }
        return feedStatus.lastRefresh("spirii")
                .map(refresh -> {
                    Duration age = Duration.between(refresh.at(), Instant.now(clock));
                    Health.Builder builder = age.compareTo(maxAge) > 0 ? Health.down() : Health.up();
                    return builder
                            .withDetail("lastRefresh", refresh.at().toString())
                            .withDetail("failedOperators", refresh.failures())
                            .withDetail("sites", store.all().size())
                            .build();
                })
                .orElseGet(() -> Health.up().withDetail("spirii", "loading").build());
    }
}
