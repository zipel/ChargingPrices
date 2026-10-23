package io.github.zipel.chargingprices.domain;

import java.time.Instant;
import java.util.List;

/**
 * One charge point (an EVSE): the unit a car plugs into and pays for.
 *
 * @param id           the feed's own ID, used to match status updates to this charge point
 * @param evseId       the public EVSE ID (e.g. {@code DK.SPI.Z507*1}), stable across feeds
 * @param maxPowerKw   the highest power of its connectors, or null if the feed doesn't say
 * @param tariff       the ad hoc price, or null if the operator publishes none
 */
public record ChargePoint(
        String id,
        String evseId,
        CurrentType currentType,
        List<Connector> connectors,
        Double maxPowerKw,
        Availability availability,
        Tariff tariff,
        Instant statusUpdated) {

    public ChargePoint {
        connectors = List.copyOf(connectors);
    }

    public SpeedClass speedClass() {
        return SpeedClass.of(maxPowerKw, currentType);
    }

    public ChargePoint withStatus(Availability newAvailability, Tariff newTariff, Instant updated) {
        return new ChargePoint(id, evseId, currentType, connectors, maxPowerKw, newAvailability,
                newTariff != null ? newTariff : tariff, updated);
    }

    public record Connector(String type, String label, Double maxPowerKw) {
    }

    public enum CurrentType { AC, DC, UNKNOWN }
}
