package io.github.zipel.chargingprices.feed.spirii;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.zip.GZIPInputStream;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fetches Spirii's public DATEX II endpoints. No API key is needed.
 *
 * <p>Sends the last ETag back as {@code If-None-Match}, so unchanged data costs a {@code 304} and no body.
 */
@Component
public class SpiriiClient {

    public static final String TABLES = "/v2/afir/energy-infrastructure-tables";
    public static final String STATUSES = "/v2/afir/energy-infrastructure-statuses";

    /** The body (null when unchanged) and the ETag to send next time. */
    public record Response(JsonNode body, String etag) {
        public boolean notModified() {
            return body == null;
        }
    }

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String country;

    public SpiriiClient(JsonMapper jsonMapper, SpiriiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .defaultHeader("Accept-Encoding", "gzip")
                .defaultHeader("User-Agent", "ChargingPrices (+https://github.com/zipel/ChargingPrices)")
                .build();
        this.jsonMapper = jsonMapper;
        this.country = properties.country();
    }

    public Response fetch(String path, int cpoId, String etag) {
        return restClient.get()
                .uri(uri -> uri.path(path)
                        .queryParam("cpoId", cpoId)
                        .queryParam("countryIsos", country)
                        .build())
                .headers(headers -> {
                    if (etag != null) {
                        headers.setIfNoneMatch(etag);
                    }
                })
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 304) {
                        return new Response(null, etag);
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("Spirii " + path + " returned " + response.getStatusCode());
                    }
                    try (InputStream body = decompress(response.getBody())) {
                        return new Response(jsonMapper.readTree(body), response.getHeaders().getETag());
                    }
                });
    }

    /** Spirii always gzips its responses. Checks the gzip magic bytes rather than trusting the headers. */
    private static InputStream decompress(InputStream raw) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw);
        in.mark(2);
        int first = in.read();
        int second = in.read();
        in.reset();
        boolean gzip = first == 0x1f && second == 0x8b;
        return gzip ? new GZIPInputStream(in) : in;
    }
}
