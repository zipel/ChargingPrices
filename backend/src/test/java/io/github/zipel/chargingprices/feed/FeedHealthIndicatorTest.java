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
    private final FeedHealthIndicator indicator = new FeedHealthIndicator(feedStatus, new SiteStore(), clock, Duration.ofMinutes(25));

    private void register(String key) {
        feedStatus.register(new FeedStatus.Source(key, key, "https://example.invalid", "test", null));
    }

    @Test
    void isUpWhileTheFirstLoadIsRunning() {
        register("spirii");
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void isUpWithFreshData() {
        register("spirii");
        feedStatus.markSucceeded("spirii", NOW.minus(Duration.ofMinutes(3)), 0);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void isDownWhenTheDataIsStale() {
        register("spirii");
        feedStatus.markSucceeded("spirii", NOW.minus(Duration.ofMinutes(40)), 0);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void isDownWhenAFeedHasNeverSucceeded() {
        register("spirii");
        feedStatus.markFailed("spirii", NOW.minus(Duration.ofMinutes(1)));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void oneStaleFeedMakesTheAppDownEvenIfAnotherIsFresh() {
        register("spirii");
        register("ecomovement");
        feedStatus.markSucceeded("spirii", NOW.minus(Duration.ofMinutes(1)), 0);
        feedStatus.markSucceeded("ecomovement", NOW.minus(Duration.ofHours(2)), 0);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void isUpWhenNoFeedIsSwitchedOn() {
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }
}
