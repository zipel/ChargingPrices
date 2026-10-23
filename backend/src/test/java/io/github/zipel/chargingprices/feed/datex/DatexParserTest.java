package io.github.zipel.chargingprices.feed.datex;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zipel.chargingprices.domain.Availability;
import io.github.zipel.chargingprices.domain.ChargePoint;
import io.github.zipel.chargingprices.domain.ChargePoint.CurrentType;
import io.github.zipel.chargingprices.domain.Site;
import io.github.zipel.chargingprices.domain.Tariff;
import io.github.zipel.chargingprices.feed.datex.DatexParser.StatusUpdate;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs the parser on a real sample of Spirii's feed (three sites, fetched 2026-10-23). */
class DatexParserTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 23);
    private static final LocalDate SATURDAY = FRIDAY.plusDays(1);

    private final DatexParser parser = new DatexParser();

    @Test
    void parsesSitesWithAddressAndChargePoints() {
        List<Site> sites = parser.parseTable(fixture("tables.json"), "spirii");

        assertThat(sites).extracting(Site::id)
                .containsExactlyInAnyOrder("spirii-site-1077", "spirii-site-6262", "spirii-site-10094");

        Site site = find(sites, "spirii-site-1077");
        assertThat(site.name()).isEqualTo("Slotslyngvej");
        assertThat(site.operator()).isEqualTo("Spirii ApS");
        assertThat(site.latitude()).isBetween(54.5, 57.8);
        assertThat(site.longitude()).isBetween(8.0, 15.2);
        assertThat(site.chargePoints()).hasSize(2);

        ChargePoint point = site.chargePoints().getFirst();
        assertThat(point.id()).isEqualTo("cp-DK.SPI.Z021*1-1");
        assertThat(point.evseId()).isEqualTo("DK.SPI.Z021*1");
        assertThat(point.currentType()).isEqualTo(CurrentType.AC);
        assertThat(point.maxPowerKw()).isEqualTo(22.0);
        assertThat(point.connectors()).extracting(ChargePoint.Connector::label).containsExactly("Type 2");
        assertThat(point.tariff()).isNotNull();
        assertThat(point.tariff().currency()).isEqualTo("DKK");
    }

    @Test
    void parsesAvailabilityAndDynamicPrices() {
        Map<String, StatusUpdate> updates = parser.parseStatuses(fixture("statuses.json"));

        StatusUpdate update = updates.get("cp-DK.SPI.Z021*1-1");
        assertThat(update.availability()).isEqualTo(Availability.OUT_OF_SERVICE);

        Tariff tariff = update.tariff();
        assertThat(tariff.priceAt(FRIDAY.atTime(23, 5))).contains(new BigDecimal("3.46"));
        assertThat(tariff.priceAt(SATURDAY.atTime(0, 0))).contains(new BigDecimal("3.03"));
        assertThat(tariff.priceAt(SATURDAY.atTime(22, 50))).contains(new BigDecimal("2.9"));
        assertThat(tariff.fallbackPerKwh()).isEqualByComparingTo("2.68");
    }

    @Test
    void parsesTimeOfUsePrices() {
        Tariff tariff = parser.parseStatuses(fixture("statuses.json")).get("cp-DK.SPI.Z5578*1-1").tariff();

        assertThat(tariff.priceAt(SATURDAY.atTime(17, 0))).contains(new BigDecimal("3.0"));
        assertThat(tariff.priceAt(SATURDAY.atTime(10, 0))).contains(new BigDecimal("2.75"));
    }

    @Test
    void parsesPerMinuteFees() {
        Tariff tariff = parser.parseStatuses(fixture("statuses.json")).get("cp-ZW0057*1-1").tariff();

        assertThat(tariff.minuteFees()).singleElement().satisfies(fee -> {
            assertThat(fee.perMinute()).isEqualByComparingTo("0.25");
            assertThat(fee.fromMinute()).isEqualTo(240);
            // Only on weekdays between 06:00 and 17:00
            assertThat(fee.startMinute()).isEqualTo(6 * 60);
            assertThat(fee.endMinute()).isEqualTo(17 * 60);
            assertThat(fee.days()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        });
    }

    private static Site find(List<Site> sites, String id) {
        return sites.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    static JsonNode fixture(String name) {
        try (InputStream in = DatexParserTest.class.getResourceAsStream("/fixtures/spirii/" + name)) {
            return JsonMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
