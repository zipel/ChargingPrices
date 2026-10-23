package io.github.zipel.chargingprices.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zipel.chargingprices.domain.Tariff.PeriodPrice;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TariffTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 23);
    private static final LocalDate SATURDAY = FRIDAY.plusDays(1);

    /** The shape of a real dynamic tariff: 15-minute slots, each valid for two days, plus a fallback. */
    private final Tariff dynamic = new Tariff(
            List.of(
                    slot(FRIDAY, "22:45", "23:00", "3.39"),
                    slot(FRIDAY, "23:45", "24:00", "3.30"),
                    slot(SATURDAY, "00:00", "00:15", "3.03"),
                    slot(SATURDAY, "22:45", "23:00", "2.90")),
            new BigDecimal("2.68"), null, List.of(), "DKK");

    @Test
    void usesTheSlotThatContainsTheTime() {
        assertThat(dynamic.priceAt(FRIDAY.atTime(22, 50))).contains(new BigDecimal("3.39"));
    }

    @Test
    void prefersTodaysSlotOverYesterdaysWhenBothCoverTheTime() {
        // Friday's 22:45 slot is valid until Saturday too, but Saturday's own slot is newer
        assertThat(dynamic.priceAt(SATURDAY.atTime(22, 50))).contains(new BigDecimal("2.90"));
    }

    @Test
    void readsDatesAsLocalDatesJustAfterMidnight() {
        // 00:05 in Copenhagen is still Friday in UTC; the slot is dated Saturday and must still match
        assertThat(dynamic.priceAt(SATURDAY.atTime(0, 5))).contains(new BigDecimal("3.03"));
    }

    @Test
    void treats2400AsEndOfDay() {
        assertThat(dynamic.priceAt(FRIDAY.atTime(23, 59))).contains(new BigDecimal("3.30"));
    }

    @Test
    void fallsBackWhenNoSlotMatches() {
        assertThat(dynamic.priceAt(SATURDAY.atTime(10, 0))).contains(new BigDecimal("2.68"));
    }

    @Test
    void repeatsTimeOfUseWindowsEveryDayIncludingOvernight() {
        Tariff nightRate = new Tariff(
                List.of(new PeriodPrice(LocalDate.of(2024, 1, 1), null, 22 * 60, 6 * 60, new BigDecimal("1.99"))),
                new BigDecimal("3.49"), null, List.of(), "DKK");

        assertThat(nightRate.priceAt(FRIDAY.atTime(3, 0))).contains(new BigDecimal("1.99"));
        assertThat(nightRate.priceAt(FRIDAY.atTime(23, 0))).contains(new BigDecimal("1.99"));
        assertThat(nightRate.priceAt(FRIDAY.atTime(12, 0))).contains(new BigDecimal("3.49"));
    }

    @Test
    void hasNoPriceWhenNothingIsPublished() {
        Tariff empty = new Tariff(List.of(), null, null, List.of(), "DKK");

        assertThat(empty.priceAt(FRIDAY.atTime(12, 0))).isEmpty();
        assertThat(empty.hasAnyPrice()).isFalse();
    }

    @Test
    void curveStartsAtTheCurrentSlotAndSteps15Minutes() {
        List<Tariff.CurvePoint> curve = dynamic.curve(LocalDateTime.of(FRIDAY, java.time.LocalTime.of(22, 52)), Duration.ofHours(1));

        assertThat(curve).extracting(Tariff.CurvePoint::start).containsExactly(
                FRIDAY.atTime(22, 45), FRIDAY.atTime(23, 0), FRIDAY.atTime(23, 15), FRIDAY.atTime(23, 30));
        assertThat(curve.getFirst().perKwh()).isEqualByComparingTo("3.39");
        assertThat(curve.get(1).perKwh()).isEqualByComparingTo("2.68");
    }

    private static PeriodPrice slot(LocalDate date, String start, String end, String price) {
        return new PeriodPrice(date, date.plusDays(1), minutes(start), minutes(end), new BigDecimal(price));
    }

    private static int minutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}
