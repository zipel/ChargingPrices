package io.github.zipel.chargingprices.domain;

import java.time.ZoneId;

public final class Denmark {

    /** Operators publish price windows in local time. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Copenhagen");

    private Denmark() {
    }
}
