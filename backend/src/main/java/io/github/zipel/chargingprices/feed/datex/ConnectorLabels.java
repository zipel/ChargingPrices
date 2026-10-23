package io.github.zipel.chargingprices.feed.datex;

import java.util.Map;

/** Plain names for the DATEX II connector types, as drivers know them. */
final class ConnectorLabels {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("iec62196T2", "Type 2"),
            Map.entry("iec62196T2COMBO", "CCS"),
            Map.entry("iec62196T1", "Type 1"),
            Map.entry("iec62196T1COMBO", "CCS1"),
            Map.entry("iec62196T3A", "Type 3A"),
            Map.entry("iec62196T3C", "Type 3C"),
            Map.entry("chademo", "CHAdeMO"),
            Map.entry("teslaS", "Tesla"),
            Map.entry("teslaR", "Tesla"),
            Map.entry("domesticF", "Schuko"),
            Map.entry("domesticE", "Type E"));

    private ConnectorLabels() {
    }

    static String label(String type) {
        if (type == null) {
            return "Other";
        }
        return LABELS.getOrDefault(type, "Other");
    }
}
