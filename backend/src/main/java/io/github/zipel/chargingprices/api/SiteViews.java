package io.github.zipel.chargingprices.api;

import io.github.zipel.chargingprices.domain.Availability;
import io.github.zipel.chargingprices.domain.SpeedClass;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/** The JSON shapes the API returns. Kept separate from the domain so the API can stay stable while the model changes. */
public final class SiteViews {

    private SiteViews() {
    }

    /** Everything the map needs for one site, kept small because the map loads all sites at once. */
    public record SiteSummary(
            String id,
            String name,
            String operator,
            double lat,
            double lon,
            String street,
            String city,
            boolean restricted,
            BigDecimal priceNow,
            int available,
            int total,
            List<SpeedGroup> groups) {
    }

    /** The charge points of one speed class at a site. */
    public record SpeedGroup(SpeedClass speed, Double maxPowerKw, BigDecimal priceNow, int available, int total) {
    }

    public record SitesResponse(Instant generatedAt, List<SiteSummary> sites) {
    }

    public record SiteDetail(
            String id,
            String name,
            String operator,
            double lat,
            double lon,
            String street,
            String postcode,
            String city,
            boolean openAllHours,
            boolean restricted,
            String source,
            BigDecimal priceNow,
            List<ChargePointView> chargePoints,
            List<PriceCurve> curves,
            Instant statusUpdated) {
    }

    public record ChargePointView(
            String evseId,
            String currentType,
            SpeedClass speed,
            Double maxPowerKw,
            List<ConnectorView> connectors,
            Availability availability,
            BigDecimal priceNow,
            BigDecimal startFee,
            List<MinuteFeeView> minuteFees) {
    }

    public record ConnectorView(String type, String label, Double maxPowerKw) {
    }

    /** {@code from} and {@code until} are local times such as "06:00", or null for all day. {@code days} is empty for every day. */
    public record MinuteFeeView(BigDecimal perMinute, int fromMinute, String from, String until, List<String> days) {
    }

    /** The cheapest price per 15-minute slot over the next 24 hours, for one speed class. */
    public record PriceCurve(SpeedClass speed, List<PricePoint> points) {
    }

    public record PricePoint(OffsetDateTime start, BigDecimal price) {
    }

    public record Meta(
            Instant generatedAt,
            boolean loading,
            int siteCount,
            int pricedSiteCount,
            List<OperatorCount> operators,
            List<SourceInfo> sources) {
    }

    public record OperatorCount(String name, int sites) {
    }

    /** {@code licenceUrl} may be null. {@code lastRefresh} is the last successful refresh. */
    public record SourceInfo(String name, String url, String licence, String licenceUrl, Instant lastRefresh) {
    }
}
