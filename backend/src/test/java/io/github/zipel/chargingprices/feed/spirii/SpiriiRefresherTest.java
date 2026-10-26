package io.github.zipel.chargingprices.feed.spirii;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.zipel.chargingprices.feed.FeedStatus;
import io.github.zipel.chargingprices.feed.datex.DatexParser;
import io.github.zipel.chargingprices.store.SiteStore;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpiriiRefresherTest {

    private final SpiriiClient client = mock(SpiriiClient.class);
    private final FeedStatus feedStatus = new FeedStatus();
    private final SpiriiRefresher refresher = new SpiriiRefresher(client, new DatexParser(), new SiteStore(),
            new SpiriiProperties(true, URI.create("https://example.invalid"), "DK", List.of(1, 4, 7),
                    Duration.ofMinutes(2), Duration.ofHours(6), Duration.ZERO),
            feedStatus, Clock.systemUTC());

    @Test
    void stopsWithoutFetchingOnceTheAppIsShuttingDown() {
        refresher.stopRefreshing();

        refresher.refresh();

        verify(client, never()).fetch(any(), anyInt(), any());
        // An unfinished run doesn't count as a refresh, so the health check doesn't report fresh data
        assertThat(feedStatus.refresh("spirii")).isEmpty();
    }
}
