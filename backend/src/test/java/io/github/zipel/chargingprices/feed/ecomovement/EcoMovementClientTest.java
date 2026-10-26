package io.github.zipel.chargingprices.feed.ecomovement;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class EcoMovementClientTest {

    private static final URI FIRST = URI.create("https://nap-dk.eco-movement.com/datex2/v1/locations");

    @Test
    void followsTheNextLinkFromTheHeader() {
        // The real header, from a response on 2026-10-26
        String header = "<https://nap-dk.eco-movement.com/datex2/v1/locations?limit=1000&offset=1000>; rel=\"next\"";

        assertThat(EcoMovementClient.nextPage(header, FIRST))
                .contains(URI.create("https://nap-dk.eco-movement.com/datex2/v1/locations?limit=1000&offset=1000"));
    }

    @Test
    void resolvesARelativeNextLink() {
        assertThat(EcoMovementClient.nextPage("</datex2/v1/locations?offset=2000>; rel=next", FIRST))
                .contains(URI.create("https://nap-dk.eco-movement.com/datex2/v1/locations?offset=2000"));
    }

    @Test
    void stopsWhenThereIsNoNextPage() {
        assertThat(EcoMovementClient.nextPage(null, FIRST)).isEmpty();
        assertThat(EcoMovementClient.nextPage("<https://nap-dk.eco-movement.com/x>; rel=\"prev\"", FIRST)).isEmpty();
    }

    @Test
    void neverSendsTheTokenToAnotherHost() {
        assertThat(EcoMovementClient.nextPage("<https://evil.example/steal>; rel=\"next\"", FIRST)).isEmpty();
        assertThat(EcoMovementClient.nextPage("<http://nap-dk.eco-movement.com/plain-http>; rel=\"next\"", FIRST)).isEmpty();
    }
}
