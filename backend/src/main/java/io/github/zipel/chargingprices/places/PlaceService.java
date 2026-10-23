package io.github.zipel.chargingprices.places;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Place search with the two things Nominatim's usage policy asks for: a cache, and at most one request
 * per second to the geocoder.
 */
@Service
public class PlaceService {

    static final int MIN_LENGTH = 2;
    static final int MAX_LENGTH = 100;
    private static final int CACHE_SIZE = 500;
    private static final Duration MIN_GAP = Duration.ofSeconds(1);

    private final PlaceLookup lookup;
    private final Clock clock;
    private Instant lastCall = Instant.EPOCH;

    /** Least recently used entries are dropped first once the cache is full. */
    private final Map<String, List<Place>> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Place>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public PlaceService(PlaceLookup lookup, Clock clock) {
        this.lookup = lookup;
        this.clock = clock;
    }

    /** Synchronized so concurrent searches queue up instead of breaking the one-per-second rule. */
    public synchronized List<Place> search(String query) {
        String normalised = query == null ? "" : query.strip().replaceAll("\\s+", " ");
        if (normalised.length() < MIN_LENGTH || normalised.length() > MAX_LENGTH) {
            return List.of();
        }
        String key = normalised.toLowerCase(Locale.ROOT);
        List<Place> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        waitForTurn();
        List<Place> places = List.copyOf(lookup.search(normalised));
        cache.put(key, places);
        return places;
    }

    private void waitForTurn() {
        Duration sinceLast = Duration.between(lastCall, Instant.now(clock));
        if (sinceLast.compareTo(MIN_GAP) < 0) {
            try {
                Thread.sleep(MIN_GAP.minus(sinceLast));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCall = Instant.now(clock);
    }
}
