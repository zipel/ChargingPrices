package io.github.zipel.chargingprices.domain;

import java.util.List;

/** A charging location: one address with one or more charge points. */
public record Site(
        String id,
        String source,
        String name,
        String operator,
        double latitude,
        double longitude,
        String street,
        String postcode,
        String city,
        boolean openAllHours,
        List<ChargePoint> chargePoints) {

    public Site {
        chargePoints = List.copyOf(chargePoints);
    }

    public Site withChargePoints(List<ChargePoint> newChargePoints) {
        return new Site(id, source, name, operator, latitude, longitude, street, postcode, city, openAllHours, newChargePoints);
    }
}
