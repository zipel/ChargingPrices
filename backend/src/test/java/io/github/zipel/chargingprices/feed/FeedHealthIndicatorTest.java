package io.github.zipel.chargingprices.feed;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zipel.chargingprices.store.SiteStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class FeedHealthIndicatorTest {

    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FeedStatus feedStatus = new FeedStatus();

    private FeedHealthIndicator indicator(boolean enabled) {
        return new FeedHealthIndicator(feedStatus, new SiteStore(), clock, enabled, Duration.ofMinutes(10));
    }

    @Test
    void isUpWhileTheFirstLoadIsRunning() {
        assertThat(indicator(true).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void isUpWithFreshData() {
        feedStatus.markRefreshed("spirii", NOW.minus(Duration.ofMinutes(3)), 0);
        assertThat(indicator(true).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void isDownWhenTheDataIsStale() {
        feedStatus.markRefreshed("spirii", NOW.minus(Duration.ofMinutes(25)), 0);
        assertThat(indicator(true).health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void isDownWhenEveryOperatorFailedAndNothingIsLoaded() {
        feedStatus.markRefreshed("spirii", NOW.minus(Duration.ofMinutes(1)), 22);
        assertThat(indicator(true).health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void isUpWhenFeedsAreSwitchedOff() {
        assertThat(indicator(false).health().getStatus()).isEqualTo(Status.UP);
    }
}
