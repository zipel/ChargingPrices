package io.github.zipel.chargingprices.feed;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Remembers when each source was last refreshed, so the UI can show how fresh the data is. */
@Component
public class FeedStatus {

    public record Refresh(Instant at, int failures) {
    }

    private final Map<String, Refresh> refreshes = new ConcurrentHashMap<>();

    public void markRefreshed(String source, Instant at, int failures) {
        refreshes.put(source, new Refresh(at, failures));
    }

    public Optional<Refresh> lastRefresh(String source) {
        return Optional.ofNullable(refreshes.get(source));
    }

    /** True until every source has finished its first refresh. */
    public boolean loading(int expectedSources) {
        return refreshes.size() < expectedSources;
    }
}
