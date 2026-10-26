package io.github.zipel.chargingprices.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zipel.chargingprices.domain.Availability;
import io.github.zipel.chargingprices.domain.ChargePoint;
import io.github.zipel.chargingprices.domain.ChargePoint.CurrentType;
import io.github.zipel.chargingprices.domain.Site;
import java.util.List;
import org.junit.jupiter.api.Test;

class SiteDeduplicatorTest {

    @Test
    void dropsALaterSiteWithTheSameNameAtTheSamePlace() {
        // Real case: Shell's stations are in Spirii's feed and in Eco-Movement's, with different EVSE IDs
        Site spirii = site("spirii-1", "Shell Express Nibe", 56.996395, 9.668199, "DK.DCC.00923701*1");
        Site eco = site("ecomovement-1", "Shell Express Nibe", 56.996400, 9.668210, "31948*163840");

        assertThat(SiteDeduplicator.merge(List.of(List.of(spirii), List.of(eco)))).containsExactly(spirii);
    }

    @Test
    void dropsALaterSiteThatSharesAnEvseIdWrittenDifferently() {
        Site first = site("a-1", "Garage A", 55.70, 12.55, "DK.SPI.E123*1");
        Site later = site("b-1", "Some other name", 55.80, 12.60, "DK*SPI*E123*1");

        assertThat(SiteDeduplicator.merge(List.of(List.of(first), List.of(later)))).containsExactly(first);
    }

    @Test
    void keepsDifferentOperatorsInTheSameCarPark() {
        Site spirii = site("spirii-1", "P-hus Lüders", 55.7100, 12.5900, "DK.SPI.1*1");
        Site clever = site("ecomovement-1", "Clever P-hus Lüders", 55.7101, 12.5901, "DK*CLE*E1*1");

        assertThat(SiteDeduplicator.merge(List.of(List.of(spirii), List.of(clever)))).containsExactly(spirii, clever);
    }

    @Test
    void keepsSameNamedSitesThatAreFarApart() {
        Site a = site("a-1", "Netto", 55.70, 12.55, "X1");
        Site b = site("b-1", "Netto", 56.15, 10.20, "X2");

        assertThat(SiteDeduplicator.merge(List.of(List.of(a), List.of(b)))).containsExactly(a, b);
    }

    @Test
    void neverMergesSitesWithinOneFeed() {
        Site a = site("a-1", "Twin", 55.70, 12.55, "X1");
        Site b = site("a-2", "Twin", 55.70, 12.55, "X2");

        assertThat(SiteDeduplicator.merge(List.of(List.of(a, b)))).containsExactly(a, b);
    }

    private static Site site(String id, String name, double lat, double lon, String evseId) {
        ChargePoint point = new ChargePoint(id + "-cp", evseId, CurrentType.AC, List.of(), 22.0, Availability.AVAILABLE, null, null);
        return new Site(id, id.split("-")[0], name, "Operator", lat, lon, null, null, null, false, List.of(point));
    }
}
