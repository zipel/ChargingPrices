package io.github.zipel.chargingprices.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The ad hoc (pay as you go) price of a charge point, VAT included.
 *
 * <p>Operators publish prices in three shapes, and this model covers all of them:
 * <ul>
 *   <li>Dynamic prices: one entry per 15-minute slot for today and tomorrow.</li>
 *   <li>Time-of-use prices: a few daily windows (e.g. 17:00–21:00) that repeat every day.</li>
 *   <li>A fallback price with no time window, used when no window matches.</li>
 * </ul>
 * All times are local Danish time.
 */
public record Tariff(
        List<PeriodPrice> periodPrices,
        BigDecimal fallbackPerKwh,
        BigDecimal startFee,
        List<MinuteFee> minuteFees,
        String currency) {

    public Tariff {
        periodPrices = List.copyOf(periodPrices);
        minuteFees = List.copyOf(minuteFees);
    }

    public static final Duration SLOT = Duration.ofMinutes(15);

    /** A price per kWh that applies between two times of day, on the dates from {@code firstDate} to {@code lastDate}. */
    public record PeriodPrice(LocalDate firstDate, LocalDate lastDate, int startMinute, int endMinute, BigDecimal perKwh) {

        /** {@code lastDate} may be null, meaning the window repeats with no end date. */
        boolean appliesOn(LocalDate date) {
            return !date.isBefore(firstDate) && (lastDate == null || !date.isAfter(lastDate));
        }

        /** Minutes are counted from midnight; an end of 1440 means "24:00". Windows past midnight wrap around. */
        boolean coversMinute(int minuteOfDay) {
            if (startMinute < endMinute) {
                return minuteOfDay >= startMinute && minuteOfDay < endMinute;
            }
            return minuteOfDay >= startMinute || minuteOfDay < endMinute;
        }
    }

    /**
     * A fee per minute that starts after {@code fromMinute} minutes, typically to stop cars blocking a charger.
     * It may only apply between two times of day ({@code startMinute}, {@code endMinute}, null for all day)
     * and on some days ({@code days}, empty for every day).
     */
    public record MinuteFee(BigDecimal perMinute, int fromMinute, Integer startMinute, Integer endMinute, List<DayOfWeek> days) {

        public MinuteFee {
            days = List.copyOf(days);
        }
    }

    /** One point on a price curve. */
    public record CurvePoint(LocalDateTime start, BigDecimal perKwh) {
    }

    /**
     * The price per kWh at the given local time.
     *
     * <p>Dynamic prices are published with a two-day validity, so the same time of day can match both
     * today's and yesterday's entry. The entry with the latest first date wins.
     */
    public Optional<BigDecimal> priceAt(LocalDateTime localTime) {
        LocalDate date = localTime.toLocalDate();
        int minute = localTime.getHour() * 60 + localTime.getMinute();
        return periodPrices.stream()
                .filter(p -> p.appliesOn(date) && p.coversMinute(minute))
                .max(Comparator.comparing(PeriodPrice::firstDate))
                .map(PeriodPrice::perKwh)
                .or(() -> Optional.ofNullable(fallbackPerKwh));
    }

    /**
     * Prices for the coming hours in 15-minute steps, starting with the slot that contains {@code from}.
     *
     * <p>For a dynamic tariff the curve stops where the published slots end. Tomorrow's prices usually
     * arrive around 13:00, and until then the fallback price would only be a guess, not a forecast.
     */
    public List<CurvePoint> curve(LocalDateTime from, Duration length) {
        boolean dynamic = periodPrices.stream().anyMatch(p -> p.lastDate() != null);
        LocalDateTime slotStart = from.withSecond(0).withNano(0).minusMinutes(from.getMinute() % SLOT.toMinutes());
        LocalDateTime end = slotStart.plus(length);
        List<CurvePoint> points = new ArrayList<>();
        for (LocalDateTime t = slotStart; t.isBefore(end); t = t.plus(SLOT)) {
            LocalDateTime start = t;
            Optional<BigDecimal> price = dynamic ? publishedPriceAt(t) : priceAt(t);
            price.ifPresent(p -> points.add(new CurvePoint(start, p)));
        }
        return points;
    }

    /** The price of a dynamic slot published for that very date, if there is one. */
    private Optional<BigDecimal> publishedPriceAt(LocalDateTime localTime) {
        LocalDate date = localTime.toLocalDate();
        int minute = localTime.getHour() * 60 + localTime.getMinute();
        return periodPrices.stream()
                .filter(p -> p.lastDate() != null && p.firstDate().equals(date) && p.coversMinute(minute))
                .findFirst()
                .map(PeriodPrice::perKwh);
    }

    public boolean hasAnyPrice() {
        return !periodPrices.isEmpty() || fallbackPerKwh != null;
    }
}
