package io.github.zipel.chargingprices.places;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Place search with the two things Nominatim's usage policy asks for: a cache, and at most one request
 * per second to the geocoder.
 *
 * <p>Requests that aren't cached queue up for the geocoder one at a time. The queue is kept short, so a
 * burst of searches gets a quick "429 Too Many Requests" instead of holding server threads for a long time.
 */
@Service
public class PlaceService {

    static final int MIN_LENGTH = 2;
    static final int MAX_LENGTH = 100;
    static final int MAX_QUEUED = 3;
    private static final int CACHE_SIZE = 500;
    private static final Duration MIN_GAP = Duration.ofSeconds(1);

    private final PlaceLookup lookup;
    private final Clock clock;
    private final AtomicInteger queued = new AtomicInteger();
    private final Object geocoderTurn = new Object();
    private Instant lastCall = Instant.EPOCH;

    /** Least recently used entries are dropped first once the cache is full. Guarded by itself. */
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

    public List<Place> search(String query) {
        String normalised = query == null ? "" : query.strip().replaceAll("\\s+", " ");
        if (normalised.length() < MIN_LENGTH || normalised.length() > MAX_LENGTH) {
            return List.of();
        }
        String key = normalised.toLowerCase(Locale.ROOT);
        List<Place> cached = cached(key);
        if (cached != null) {
            return cached;
        }

        if (queued.incrementAndGet() > MAX_QUEUED) {
            queued.decrementAndGet();
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many searches at once, try again in a moment");
        }
        try {
            synchronized (geocoderTurn) {
                // Someone ahead in the queue may have searched for the same thing
                cached = cached(key);
                if (cached != null) {
                    return cached;
                }
                waitForTurn();
                List<Place> places = List.copyOf(lookup.search(normalised));
                synchronized (cache) {
                    cache.put(key, places);
                }
                return places;
            }
        } catch (RestClientException e) {
            // Not cached, so the next search tries the geocoder again
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Place search is unavailable right now", e);
        } finally {
            queued.decrementAndGet();
        }
    }

    private List<Place> cached(String key) {
        synchronized (cache) {
            return cache.get(key);
        }
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
