package com.aurora.observation.service;

import com.aurora.observation.dto.Location;
import com.aurora.observation.provider.GeocodingProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LocationService {
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Duration SEARCH_TTL = Duration.ofMinutes(10);
    private static final Duration LOCATION_TTL = Duration.ofHours(1);

    private final GeocodingProvider geocoding;
    private final Clock clock;
    private final Map<String, CacheEntry<List<Location>>> searches = new LinkedHashMap<>();
    private final Map<Long, CacheEntry<Location>> locations = new LinkedHashMap<>();

    public LocationService(GeocodingProvider geocoding, Clock clock) {
        this.geocoding = geocoding;
        this.clock = clock;
    }

    public List<Location> search(String query) {
        if (query == null) throw new InvalidLocationRequestException("Enter a location name between 2 and 80 characters.");
        String cleaned = query.strip();
        if (cleaned.length() < 2 || cleaned.length() > 80) {
            throw new InvalidLocationRequestException("Enter a location name between 2 and 80 characters.");
        }
        String key = cleaned.toLowerCase(Locale.ROOT);
        List<Location> cached = read(searches, key);
        if (cached != null) return cached;

        List<Location> result = List.copyOf(geocoding.search(cleaned));
        write(searches, key, result, SEARCH_TTL);
        for (Location location : result) write(locations, location.id(), location, LOCATION_TTL);
        return result;
    }

    public Location get(long id) {
        if (id <= 0) throw new InvalidLocationRequestException("Location ID must be positive.");
        Location cached = read(locations, id);
        if (cached != null) return cached;

        Location location = geocoding.get(id).orElseThrow(() -> new LocationNotFoundException(id));
        write(locations, id, location, LOCATION_TTL);
        return location;
    }

    private <K, V> V read(Map<K, CacheEntry<V>> cache, K key) {
        synchronized (cache) {
            CacheEntry<V> entry = cache.get(key);
            if (entry == null) return null;
            if (!clock.instant().isBefore(entry.expiresAt())) {
                cache.remove(key);
                return null;
            }
            return entry.value();
        }
    }

    private <K, V> void write(Map<K, CacheEntry<V>> cache, K key, V value, Duration ttl) {
        synchronized (cache) {
            if (cache.size() >= MAX_CACHE_ENTRIES && !cache.containsKey(key)) {
                cache.remove(cache.keySet().iterator().next());
            }
            cache.put(key, new CacheEntry<>(value, clock.instant().plus(ttl)));
        }
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {}
}
