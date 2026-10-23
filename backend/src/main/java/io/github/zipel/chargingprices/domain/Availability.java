package io.github.zipel.chargingprices.domain;

/** Whether a charge point can be used right now, simplified from the DATEX II status values. */
public enum Availability {
    AVAILABLE,
    OCCUPIED,
    OUT_OF_SERVICE,
    UNKNOWN;

    public static Availability fromDatex(String status) {
        if (status == null) {
            return UNKNOWN;
        }
        return switch (status) {
            case "available" -> AVAILABLE;
            // "blocked" means something is in the way, usually a parked car, so it's unusable but not broken
            case "occupied", "charging", "blocked", "reserved" -> OCCUPIED;
            case "inoperative", "outOfOrder", "faulted", "removed" -> OUT_OF_SERVICE;
            default -> UNKNOWN;
        };
    }
}
