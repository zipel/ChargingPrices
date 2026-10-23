package io.github.zipel.chargingprices.domain;

import io.github.zipel.chargingprices.domain.ChargePoint.CurrentType;

/** How fast a charge point is, in the groups drivers think in. */
public enum SpeedClass {
    /** Up to 49 kW. Almost always AC, for charging while parked. */
    STANDARD,
    /** 50–149 kW DC. */
    FAST,
    /** 150 kW and up. */
    ULTRA;

    public static SpeedClass of(Double maxPowerKw, CurrentType currentType) {
        if (maxPowerKw == null || maxPowerKw <= 0) {
            return currentType == CurrentType.DC ? FAST : STANDARD;
        }
        if (maxPowerKw >= 150) {
            return ULTRA;
        }
        return maxPowerKw >= 50 ? FAST : STANDARD;
    }
}
