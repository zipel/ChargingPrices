package io.github.zipel.chargingprices.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zipel.chargingprices.api.SiteViews.SiteDetail;
import io.github.zipel.chargingprices.api.SiteViews.SiteSummary;
import io.github.zipel.chargingprices.domain.Availability;
import io.github.zipel.chargingprices.domain.SpeedClass;
import io.github.zipel.chargingprices.feed.FeedStatus;
import io.github.zipel.chargingprices.feed.datex.DatexParser;
import io.github.zipel.chargingprices.store.SiteStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SiteQueryServiceTest {

    /** 21:00 UTC is 23:00 in Copenhagen (summer time). */
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T21:00:00Z"), ZoneOffset.UTC);

    private final SiteStore store = new SiteStore();
    private final SiteQueryService service = new SiteQueryService(store, new FeedStatus(), clock);

    @BeforeEach
    void loadFixtures() {
        DatexParser parser = new DatexParser();
        store.replacePart("spirii/1", parser.parseTable(fixture("tables.json"), "spirii"));
        store.applyStatuses("spirii/1", parser.parseStatuses(fixture("statuses.json")));
    }

    @Test
    void summarisesEachSiteWithItsCurrentPrice() {
        SiteSummary dynamic = summary("spirii-site-1077");
        assertThat(dynamic.priceNow()).isEqualByComparingTo("3.46");
        assertThat(dynamic.total()).isEqualTo(2);
        assertThat(dynamic.available()).isZero();
        assertThat(dynamic.groups()).singleElement().satisfies(group -> {
            assertThat(group.speed()).isEqualTo(SpeedClass.STANDARD);
            assertThat(group.maxPowerKw()).isEqualTo(22.0);
        });

        // The 16:00–20:00 window is over, so the fallback applies
        assertThat(summary("spirii-site-6262").priceNow()).isEqualByComparingTo("2.75");
        assertThat(summary("spirii-site-6262").available()).isEqualTo(3);
    }

    @Test
    void detailIncludesChargePointsAndA24HourCurve() {
        SiteDetail detail = service.site("spirii-site-10094").orElseThrow();

        assertThat(detail.chargePoints()).hasSize(2).allSatisfy(point -> {
            assertThat(point.availability()).isEqualTo(Availability.AVAILABLE);
            assertThat(point.priceNow()).isEqualByComparingTo("2.92");
            assertThat(point.minuteFees()).hasSize(1);
        });
        assertThat(detail.curves()).singleElement().satisfies(curve -> {
            assertThat(curve.points()).hasSize(96);
            assertThat(curve.points().getFirst().start().toLocalTime()).hasToString("23:00");
        });
    }

    @Test
    void unknownSiteIsEmpty() {
        assertThat(service.site("spirii-site-0")).isEmpty();
    }

    private SiteSummary summary(String id) {
        return service.sites().sites().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    private static JsonNode fixture(String name) {
        try (var in = SiteQueryServiceTest.class.getResourceAsStream("/fixtures/spirii/" + name)) {
            return JsonMapper.builder().build().readTree(in);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
