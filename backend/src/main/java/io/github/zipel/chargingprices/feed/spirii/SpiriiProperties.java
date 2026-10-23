package io.github.zipel.chargingprices.feed.spirii;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for Spirii's public AFIR feed.
 *
 * @param cpoIds operator IDs to fetch. The feed returns at most 1,000 sites per request, so it has to be
 *               queried one operator at a time. See docs/data-sources.md for how the list was found.
 */
@ConfigurationProperties("feeds.spirii")
public record SpiriiProperties(
        boolean enabled,
        URI baseUrl,
        String country,
        List<Integer> cpoIds,
        Duration statusesRefresh,
        Duration tablesRefresh,
        Duration pauseBetweenRequests) {
}
