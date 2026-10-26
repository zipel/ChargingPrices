package io.github.zipel.chargingprices.feed.datex;

import static io.github.zipel.chargingprices.feed.datex.Json.number;
import static io.github.zipel.chargingprices.feed.datex.Json.text;

import io.github.zipel.chargingprices.domain.Tariff;
import io.github.zipel.chargingprices.domain.Tariff.MinuteFee;
import io.github.zipel.chargingprices.domain.Tariff.PeriodPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Turns a DATEX II {@code energyPrice} list into a {@link Tariff}.
 *
 * <p>How the feeds use the fields (checked against Spirii's feed, October 2026):
 * <ul>
 *   <li>{@code overallStartTime} carries the local date the price is for, written as midnight UTC
 *       ({@code 2026-10-23T00:00:00Z}), so only its date part is used.</li>
 *   <li>{@code recurringTimePeriodOfDay} holds local times such as {@code 21:45} to {@code 22:00}, and
 *       {@code 24:00} for end of day.</li>
 *   <li>A price without {@code overallPeriod} is the fallback for times that no window covers.</li>
 * </ul>
 */
final class TariffParser {

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private TariffParser() {
    }

    static Tariff parse(JsonNode energyPrices, String currency) {
        List<PeriodPrice> periodPrices = new ArrayList<>();
        List<MinuteFee> minuteFees = new ArrayList<>();
        BigDecimal fallback = null;
        BigDecimal startFee = null;

        for (JsonNode price : energyPrices) {
            BigDecimal value = priceWithVat(price);
            if (value == null) {
                continue;
            }
            String type = Json.enumValue(price.path("priceType"));
            switch (type == null ? "" : type) {
                case "pricePerKWh" -> {
                    JsonNode period = price.path("overallPeriod");
                    if (period.isMissingNode()) {
                        if (fallback == null) {
                            fallback = value;
                        }
                    } else {
                        periodPrices.addAll(periodPrices(period, value));
                    }
                }
                case "flatRate" -> startFee = value;
                case "pricePerMinute" -> minuteFees.add(minuteFee(price, value));
                default -> {
                    // Other price types (e.g. per hour) aren't used by Danish feeds yet
                }
            }
        }
        return new Tariff(periodPrices, fallback, startFee, minuteFees, currency == null ? "DKK" : currency);
    }

    /** A per-minute fee, with the time of day and the weekdays it applies to, when the feed gives them. */
    private static MinuteFee minuteFee(JsonNode price, BigDecimal value) {
        Double fromMinute = number(price.path("timeBasedApplicability").path("fromMinute"));
        Integer start = null;
        Integer end = null;
        List<DayOfWeek> days = new ArrayList<>();
        for (JsonNode validPeriod : price.path("overallPeriod").path("validPeriod")) {
            for (JsonNode timeOfDay : validPeriod.path("recurringTimePeriodOfDay")) {
                start = minuteOfDay(text(timeOfDay.path("startTimeOfPeriod")));
                end = minuteOfDay(text(timeOfDay.path("endTimeOfPeriod")));
                break;
            }
            for (JsonNode dayPeriod : validPeriod.path("recurringDayWeekMonthPeriod")) {
                for (JsonNode day : dayPeriod.path("comDayWeekMonth").path("applicableDay")) {
                    dayOfWeek(Json.enumValue(day)).ifPresent(days::add);
                }
            }
            break;
        }
        return new MinuteFee(value, fromMinute == null ? 0 : fromMinute.intValue(), start, end, days);
    }

    private static Optional<DayOfWeek> dayOfWeek(String name) {
        try {
            return Optional.of(DayOfWeek.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static BigDecimal priceWithVat(JsonNode price) {
        Double raw = number(price.path("value"));
        if (raw == null) {
            return null;
        }
        BigDecimal value = BigDecimal.valueOf(raw);
        if (value.scale() > 4) {
            // The feed has floating-point noise such as 0.030000000000000002
            value = value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        Double taxRate = number(price.path("taxRate"));
        boolean taxIncluded = price.path("taxIncluded").asBoolean(true);
        if (!taxIncluded && taxRate != null && taxRate > 0) {
            // DATEX II gives the rate in percent (25), but Eco-Movement mostly writes a fraction (0.25).
            // No real VAT rate is 1% or less, so anything up to 1 is read as a fraction.
            BigDecimal percent = taxRate <= 1 ? BigDecimal.valueOf(taxRate).multiply(HUNDRED) : BigDecimal.valueOf(taxRate);
            BigDecimal factor = BigDecimal.ONE.add(percent.divide(HUNDRED));
            value = value.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        }
        return value;
    }

    private static List<PeriodPrice> periodPrices(JsonNode period, BigDecimal value) {
        LocalDate firstDate = date(text(period.path("overallStartTime")));
        if (firstDate == null) {
            return List.of();
        }
        LocalDate lastDate = date(text(period.path("overallEndTime")));

        List<PeriodPrice> result = new ArrayList<>();
        for (JsonNode validPeriod : period.path("validPeriod")) {
            for (JsonNode timeOfDay : validPeriod.path("recurringTimePeriodOfDay")) {
                Integer start = minuteOfDay(text(timeOfDay.path("startTimeOfPeriod")));
                Integer end = minuteOfDay(text(timeOfDay.path("endTimeOfPeriod")));
                if (start != null && end != null) {
                    result.add(new PeriodPrice(firstDate, lastDate, start, end, value));
                }
            }
        }
        if (result.isEmpty()) {
            // A period with dates but no times of day covers the whole day
            result.add(new PeriodPrice(firstDate, lastDate, 0, MINUTES_PER_DAY, value));
        }
        return result;
    }

    private static LocalDate date(String timestamp) {
        if (timestamp == null || timestamp.length() < 10) {
            return null;
        }
        return LocalDate.parse(timestamp.substring(0, 10));
    }

    /** "21:45" becomes 1305; "24:00" becomes 1440. */
    static Integer minuteOfDay(String time) {
        if (time == null || !time.matches("\\d{1,2}:\\d{2}(:\\d{2})?")) {
            return null;
        }
        String[] parts = time.split(":");
        int minutes = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        return Math.min(minutes, MINUTES_PER_DAY);
    }
}
