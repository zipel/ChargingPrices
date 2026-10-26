package io.github.zipel.chargingprices.feed.ecomovement;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for Eco-Movement's AFIR feed for Denmark (NAP dataset 1170).
 *
 * @param token    access token from support@eco-movement.com; the feed is off while it's blank. It comes from
 *                 ECO_MOVEMENT_TOKEN in the git-ignored .env file or the environment, never from the repo.
 * @param url      the first page; later pages come from the response's {@code Link: rel="next"} header
 * @param refresh  how often to fetch the whole country (about 35 MB in 4 pages)
 * @param maxPages a safety limit in case the next links never end
 */
@ConfigurationProperties("feeds.eco-movement")
public record EcoMovementProperties(String token, URI url, Duration refresh, int maxPages) {
}
