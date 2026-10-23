package io.github.zipel.chargingprices.api;

import io.github.zipel.chargingprices.api.SiteViews.ChargePointView;
import io.github.zipel.chargingprices.api.SiteViews.ConnectorView;
import io.github.zipel.chargingprices.api.SiteViews.Meta;
import io.github.zipel.chargingprices.api.SiteViews.MinuteFeeView;
import io.github.zipel.chargingprices.api.SiteViews.OperatorCount;
import io.github.zipel.chargingprices.api.SiteViews.PriceCurve;
import io.github.zipel.chargingprices.api.SiteViews.PricePoint;
import io.github.zipel.chargingprices.api.SiteViews.SiteDetail;
import io.github.zipel.chargingprices.api.SiteViews.SiteSummary;
import io.github.zipel.chargingprices.api.SiteViews.SitesResponse;
import io.github.zipel.chargingprices.api.SiteViews.SourceInfo;
import io.github.zipel.chargingprices.api.SiteViews.SpeedGroup;
import io.github.zipel.chargingprices.domain.Availability;
import io.github.zipel.chargingprices.domain.ChargePoint;
import io.github.zipel.chargingprices.domain.Denmark;
import io.github.zipel.chargingprices.domain.Site;
import io.github.zipel.chargingprices.domain.SpeedClass;
import io.github.zipel.chargingprices.domain.Tariff;
import io.github.zipel.chargingprices.feed.FeedStatus;
import io.github.zipel.chargingprices.store.SiteStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Builds the API views from the store. Prices are worked out at request time, so "now" is always now. */
@Service
public class SiteQueryService {

    private static final Duration CURVE_LENGTH = Duration.ofHours(24);

    private final SiteStore store;
    private final FeedStatus feedStatus;
    private final Clock clock;

    public SiteQueryService(SiteStore store, FeedStatus feedStatus, Clock clock) {
        this.store = store;
        this.feedStatus = feedStatus;
        this.clock = clock;
    }

    public SitesResponse sites() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(Denmark.ZONE));
        List<SiteSummary> summaries = store.all().stream()
                .map(site -> summary(site, now))
                .toList();
        return new SitesResponse(Instant.now(clock), summaries);
    }

    public Optional<SiteDetail> site(String id) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(Denmark.ZONE));
        return store.byId(id).map(site -> detail(site, now));
    }

    public Meta meta() {
        List<Site> sites = store.all();
        LocalDateTime now = LocalDateTime.now(clock.withZone(Denmark.ZONE));
        int priced = (int) sites.stream().filter(site -> cheapest(site.chargePoints(), now) != null).count();
        List<OperatorCount> operators = sites.stream()
                .filter(site -> site.operator() != null)
                .collect(Collectors.groupingBy(Site::operator, TreeMap::new, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new OperatorCount(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(OperatorCount::sites).reversed())
                .toList();
        SourceInfo spirii = new SourceInfo(
                "Spirii AFIR feed (DATEX II)",
                "https://docs.spirii.com/developers/afir-compliance",
                "Published under EU AFIR Article 20",
                feedStatus.lastRefresh("spirii").map(FeedStatus.Refresh::at).orElse(null));
        return new Meta(Instant.now(clock), feedStatus.loading(1), sites.size(), priced, operators, List.of(spirii));
    }

    private SiteSummary summary(Site site, LocalDateTime now) {
        Map<SpeedClass, List<ChargePoint>> bySpeed = groupBySpeed(site.chargePoints());
        List<SpeedGroup> groups = bySpeed.entrySet().stream()
                .map(e -> new SpeedGroup(
                        e.getKey(),
                        e.getValue().stream().map(ChargePoint::maxPowerKw).filter(Objects::nonNull).max(Double::compare).orElse(null),
                        cheapest(e.getValue(), now),
                        countAvailable(e.getValue()),
                        e.getValue().size()))
                .toList();
        return new SiteSummary(
                site.id(),
                site.name(),
                site.operator(),
                site.latitude(),
                site.longitude(),
                site.street(),
                site.city(),
                cheapest(site.chargePoints(), now),
                countAvailable(site.chargePoints()),
                site.chargePoints().size(),
                groups);
    }

    private SiteDetail detail(Site site, LocalDateTime now) {
        List<ChargePointView> points = site.chargePoints().stream()
                .sorted(Comparator.comparing(ChargePoint::speedClass).thenComparing(ChargePoint::evseId, Comparator.nullsLast(String::compareTo)))
                .map(point -> chargePointView(point, now))
                .toList();

        List<PriceCurve> curves = new ArrayList<>();
        groupBySpeed(site.chargePoints()).forEach((speed, group) -> {
            List<PricePoint> curve = cheapestCurve(group, now);
            if (!curve.isEmpty()) {
                curves.add(new PriceCurve(speed, curve));
            }
        });

        Instant statusUpdated = site.chargePoints().stream()
                .map(ChargePoint::statusUpdated)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        return new SiteDetail(site.id(), site.name(), site.operator(), site.latitude(), site.longitude(),
                site.street(), site.postcode(), site.city(), site.openAllHours(), site.source(),
                cheapest(site.chargePoints(), now), points, curves, statusUpdated);
    }

    private static ChargePointView chargePointView(ChargePoint point, LocalDateTime now) {
        Tariff tariff = point.tariff();
        return new ChargePointView(
                point.evseId(),
                point.currentType().name(),
                point.speedClass(),
                point.maxPowerKw(),
                point.connectors().stream().map(c -> new ConnectorView(c.type(), c.label(), c.maxPowerKw())).toList(),
                point.availability(),
                tariff == null ? null : tariff.priceAt(now).orElse(null),
                tariff == null ? null : tariff.startFee(),
                tariff == null ? List.of() : tariff.minuteFees().stream().map(f -> new MinuteFeeView(f.perMinute(), f.fromMinute())).toList());
    }

    /** For each 15-minute slot, the lowest price among the charge points. */
    private static List<PricePoint> cheapestCurve(List<ChargePoint> points, LocalDateTime now) {
        TreeMap<LocalDateTime, BigDecimal> lowest = new TreeMap<>();
        for (ChargePoint point : points) {
            if (point.tariff() == null) {
                continue;
            }
            for (Tariff.CurvePoint slot : point.tariff().curve(now, CURVE_LENGTH)) {
                lowest.merge(slot.start(), slot.perKwh(), BigDecimal::min);
            }
        }
        return lowest.entrySet().stream()
                .map(e -> new PricePoint(e.getKey().atZone(Denmark.ZONE).toOffsetDateTime(), e.getValue()))
                .toList();
    }

    private static BigDecimal cheapest(List<ChargePoint> points, LocalDateTime now) {
        return points.stream()
                .map(ChargePoint::tariff)
                .filter(Objects::nonNull)
                .map(tariff -> tariff.priceAt(now).orElse(null))
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private static int countAvailable(List<ChargePoint> points) {
        return (int) points.stream().filter(p -> p.availability() == Availability.AVAILABLE).count();
    }

    private static Map<SpeedClass, List<ChargePoint>> groupBySpeed(List<ChargePoint> points) {
        return points.stream().collect(Collectors.groupingBy(ChargePoint::speedClass,
                () -> new EnumMap<>(SpeedClass.class), Collectors.mapping(Function.identity(), Collectors.toList())));
    }
}
