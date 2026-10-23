package io.github.zipel.chargingprices.feed.datex;

import tools.jackson.databind.JsonNode;

/** Small null-safe helpers for walking DATEX II JSON, where almost every field is optional. */
final class Json {

    private Json() {
    }

    static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.isString() ? node.stringValue() : node.toString();
    }

    static Double number(JsonNode node) {
        return node != null && node.isNumber() ? node.asDouble() : null;
    }

    /**
     * DATEX II stores free text as a list of translations: {@code {"values": [{"lang": "en", "value": "..."}]}}.
     * Returns the first translation.
     */
    static String multilingual(JsonNode node) {
        if (node == null) {
            return null;
        }
        for (JsonNode value : node.path("values")) {
            String text = text(value.path("value"));
            if (text != null && !text.isBlank()) {
                return text.strip();
            }
        }
        return null;
    }

    /** DATEX II enums look like {@code {"value": "ac"}}, or {@code {"value": "extendedG", "extendedValueG": "..."}}. */
    static String enumValue(JsonNode node) {
        String value = text(node.path("value"));
        if ("extendedG".equals(value)) {
            String extended = text(node.path("extendedValueG"));
            return extended != null ? extended : value;
        }
        return value;
    }
}
