package io.github.zipel.chargingprices.places;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Looks places up with OpenStreetMap's Nominatim geocoder.
 *
 * <p>Nominatim is free but has a usage policy: identify the app in the User-Agent, send at most one request
 * per second, and cache results. The User-Agent is set here; {@link PlaceService} does the rest.
 */
@Component
public class NominatimClient implements PlaceLookup {

    private final RestClient restClient;

    public NominatimClient(@Value("${places.nominatim-url}") String baseUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", "ChargingPrices (+https://github.com/zipel/ChargingPrices)")
                .build();
    }

    @Override
    public List<Place> search(String query) {
        JsonNode results = restClient.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "jsonv2")
                        .queryParam("countrycodes", "dk")
                        .queryParam("limit", 5)
                        .queryParam("accept-language", "da,en")
                        .build())
                .retrieve()
                .body(JsonNode.class);
        List<Place> places = new ArrayList<>();
        if (results == null) {
            return places;
        }
        for (JsonNode result : results) {
            try {
                places.add(toPlace(result));
            } catch (NumberFormatException e) {
                // A result without usable coordinates is useless on a map; skip it rather than fail the search
            }
        }
        return places;
    }

    /** Nominatim sends numbers as strings, and {@code display_name} as one long comma-separated line. */
    static Place toPlace(JsonNode result) {
        String displayName = result.path("display_name").asString("");
        String[] parts = displayName.split(",\\s*");
        String name = result.path("name").asString("");
        if (name.isBlank()) {
            name = parts.length > 1 && parts[0].matches("\\d+\\w?") ? parts[1] + " " + parts[0] : parts[0];
        }
        List<String> rest = new ArrayList<>();
        for (int i = 1; i < parts.length && rest.size() < 3; i++) {
            if (!parts[i].equals("Danmark") && !name.contains(parts[i])) {
                rest.add(parts[i]);
            }
        }

        double[] bbox = null;
        JsonNode box = result.path("boundingbox");
        if (box.isArray() && box.size() == 4) {
            try {
                // Nominatim's order is south, north, west, east
                bbox = new double[] {
                        Double.parseDouble(box.get(2).asString()), Double.parseDouble(box.get(0).asString()),
                        Double.parseDouble(box.get(3).asString()), Double.parseDouble(box.get(1).asString())};
            } catch (NumberFormatException e) {
                bbox = null; // The map then zooms to the point instead
            }
        }
        // Throws NumberFormatException for missing or broken coordinates; search() skips those results
        return new Place(name, String.join(", ", rest),
                Double.parseDouble(result.path("lat").asString("")),
                Double.parseDouble(result.path("lon").asString("")),
                bbox);
    }
}
