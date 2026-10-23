package io.github.zipel.chargingprices.places;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PlaceServiceTest {

    private final List<String> calls = new ArrayList<>();
    private final PlaceLookup fake = query -> {
        calls.add(query);
        return List.of(new Place(query, "Danmark", 55.0, 12.0, null));
    };
    private final PlaceService service = new PlaceService(fake, Clock.systemUTC());

    @Test
    void cachesResultsIgnoringCaseAndSpaces() {
        service.search("Roskilde");
        service.search("  roskilde ");

        assertThat(calls).containsExactly("Roskilde");
    }

    @Test
    void ignoresTooShortQueries() {
        assertThat(service.search("a")).isEmpty();
        assertThat(service.search("   ")).isEmpty();
        assertThat(calls).isEmpty();
    }

    @Test
    void waitsAtLeastASecondBetweenCallsToTheGeocoder() {
        long start = System.nanoTime();
        service.search("Aarhus");
        service.search("Odense");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(calls).containsExactly("Aarhus", "Odense");
        assertThat(elapsedMs).isGreaterThanOrEqualTo(900);
    }

    @Test
    void readsNominatimResults() {
        var json = JsonMapper.builder().build().readTree("""
                {"lat": "55.6415", "lon": "12.0803", "name": "Roskilde",
                 "display_name": "Roskilde, Roskilde Kommune, Region Sjælland, Danmark",
                 "boundingbox": ["55.6", "55.7", "12.0", "12.2"]}
                """);

        Place place = NominatimClient.toPlace(json);

        assertThat(place.name()).isEqualTo("Roskilde");
        assertThat(place.detail()).isEqualTo("Roskilde Kommune, Region Sjælland");
        assertThat(place.lat()).isEqualTo(55.6415);
        assertThat(place.bbox()).containsExactly(12.0, 55.6, 12.2, 55.7);
    }

    @Test
    void namesAnAddressByStreetAndNumber() {
        var json = JsonMapper.builder().build().readTree("""
                {"lat": "55.70", "lon": "12.55", "name": "",
                 "display_name": "28, Nannasgade, Nørrebro, København, Danmark"}
                """);

        assertThat(NominatimClient.toPlace(json).name()).isEqualTo("Nannasgade 28");
    }
}
