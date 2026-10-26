package io.github.zipel.chargingprices.feed.ecomovement;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fetches Eco-Movement's country snapshot page by page, sending the token as {@code Authorization: Bearer}.
 *
 * <p>Each page (up to 1,000 sites) holds both the table and the status publication. The address of the
 * next page comes in a {@code Link: <...>; rel="next"} header.
 */
@Component
public class EcoMovementClient {

    private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"?next\"?");

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final EcoMovementProperties properties;

    public EcoMovementClient(JsonMapper jsonMapper, EcoMovementProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", "ChargingPrices (+https://github.com/zipel/ChargingPrices)")
                .build();
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    /** Hands each page to {@code onPage} as soon as it's read, so the whole country is never in memory at once. */
    public int fetchAllPages(Consumer<JsonNode> onPage) {
        URI next = properties.url();
        int pages = 0;
        while (next != null) {
            if (pages >= properties.maxPages()) {
                throw new IllegalStateException("Eco-Movement sent more than " + properties.maxPages() + " pages");
            }
            URI current = next;
            next = restClient.get()
                    .uri(current)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new IllegalStateException("Eco-Movement returned " + response.getStatusCode());
                        }
                        try (InputStream body = response.getBody()) {
                            onPage.accept(jsonMapper.readTree(body));
                        }
                        return nextPage(response.getHeaders().getFirst(HttpHeaders.LINK), properties.url()).orElse(null);
                    });
            pages++;
        }
        return pages;
    }

    /**
     * The next page from a {@code Link} header. Only links to the same host are followed, so the token is never
     * sent anywhere else, whatever the header says.
     */
    static Optional<URI> nextPage(String linkHeader, URI first) {
        if (linkHeader == null) {
            return Optional.empty();
        }
        Matcher matcher = NEXT_LINK.matcher(linkHeader);
        if (!matcher.find()) {
            return Optional.empty();
        }
        URI next = first.resolve(matcher.group(1));
        boolean sameHost = "https".equalsIgnoreCase(next.getScheme()) && first.getHost().equalsIgnoreCase(next.getHost());
        return sameHost ? Optional.of(next) : Optional.empty();
    }
}
