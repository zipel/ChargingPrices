package io.github.zipel.chargingprices.feed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Knows which feeds are switched on and when each was last refreshed, so the API, the health check and the
 * UI's credits all follow the feeds that are actually running.
 */
@Component
public class FeedStatus {

    /** A data source, as shown to users. {@code licenceUrl} may be null. */
    public record Source(String key, String name, String url, String licence, String licenceUrl) {
    }

    /**
     * @param lastAttempt when the source was last fetched, successful or not
     * @param lastSuccess when it last delivered data, or null if it never has
     * @param failures    how many parts failed in the last attempt (e.g. operators in Spirii's feed)
     */
    public record Refresh(Instant lastAttempt, Instant lastSuccess, int failures) {
    }

    private final Map<String, Source> sources = new LinkedHashMap<>();
    private final Map<String, Refresh> refreshes = new ConcurrentHashMap<>();

    /** Called by each feed when it's switched on. */
    public synchronized void register(Source source) {
        sources.put(source.key(), source);
    }

    public synchronized List<Source> sources() {
        return new ArrayList<>(sources.values());
    }

    public void markSucceeded(String key, Instant at, int failures) {
        refreshes.put(key, new Refresh(at, at, failures));
    }

    /** The data from the last success stays in use; only its age keeps growing. */
    public void markFailed(String key, Instant at) {
        refreshes.compute(key, (k, old) -> new Refresh(at, old == null ? null : old.lastSuccess(), old == null ? 1 : old.failures() + 1));
    }

    public Optional<Refresh> refresh(String key) {
        return Optional.ofNullable(refreshes.get(key));
    }

    /** True until every switched-on feed has finished its first attempt. */
    public synchronized boolean loading() {
        return sources.keySet().stream().anyMatch(key -> !refreshes.containsKey(key));
    }
}
