package io.github.zipel.chargingprices.domain;

import java.math.BigDecimal;
import java.time.ZoneId;

public final class Denmark {

    /** Operators publish price windows in local time. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Copenhagen");

    /**
     * Kroner per euro. The krone is pegged to the euro in ERM II at this central rate and in practice stays
     * within half a percent of it, so a fixed rate is accurate enough for showing a few euro-priced chargers.
     */
    public static final BigDecimal EUR_TO_DKK = new BigDecimal("7.46038");

    private Denmark() {
    }
}
