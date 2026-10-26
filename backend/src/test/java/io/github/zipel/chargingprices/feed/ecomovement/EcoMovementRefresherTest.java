package io.github.zipel.chargingprices.feed.ecomovement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.zipel.chargingprices.domain.Availability;
import io.github.zipel.chargingprices.domain.ChargePoint;
import io.github.zipel.chargingprices.domain.Site;
import io.github.zipel.chargingprices.feed.FeedStatus;
import io.github.zipel.chargingprices.feed.datex.DatexParser;
import io.github.zipel.chargingprices.store.SiteStore;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs the refresher on a real four-site sample of Eco-Movement's feed (fetched 2026-10-26). */
class EcoMovementRefresherTest {

    private final EcoMovementClient client = mock(EcoMovementClient.class);
    private final SiteStore store = new SiteStore();
    private final FeedStatus feedStatus = new FeedStatus();
    private final EcoMovementRefresher refresher = new EcoMovementRefresher(client, new DatexParser(), store, feedStatus, Clock.systemUTC());

    @Test
    @SuppressWarnings("unchecked")
    void loadsSitesWithLiveStatusAndPrices() {
        when(client.fetchAllPages(any())).thenAnswer(call -> {
            ((Consumer<JsonNode>) call.getArgument(0)).accept(fixture());
            return 1;
        });

        refresher.refresh();

        assertThat(store.all()).hasSize(4);
        Site ionity = store.byId("ecomovement-IONITY-2073").orElseThrow();
        ChargePoint point = ionity.chargePoints().getFirst();
        assertThat(point.availability()).isEqualTo(Availability.OCCUPIED);
        // 3.0877 kr excluding 25% VAT
        assertThat(point.tariff().priceAt(LocalDateTime.now())).contains(new BigDecimal("3.86"));
        assertThat(feedStatus.refresh("ecomovement").orElseThrow().lastSuccess()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsThePreviousDataWhenARefreshFails() {
        when(client.fetchAllPages(any()))
                .thenAnswer(call -> {
                    ((Consumer<JsonNode>) call.getArgument(0)).accept(fixture());
                    return 1;
                })
                .thenThrow(new ResourceAccessException("timeout"));

        refresher.refresh();
        refresher.refresh();

        assertThat(store.all()).hasSize(4);
        FeedStatus.Refresh refresh = feedStatus.refresh("ecomovement").orElseThrow();
        assertThat(refresh.failures()).isEqualTo(1);
        assertThat(refresh.lastSuccess()).isBeforeOrEqualTo(refresh.lastAttempt());
    }

    @Test
    void registersItselfAsASource() {
        assertThat(feedStatus.sources()).extracting(FeedStatus.Source::key).containsExactly("ecomovement");
        assertThat(feedStatus.sources().getFirst().licence()).isEqualTo("CC BY 4.0");
    }

    static JsonNode fixture() {
        try (InputStream in = EcoMovementRefresherTest.class.getResourceAsStream("/fixtures/eco-movement/locations.json")) {
            return JsonMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
