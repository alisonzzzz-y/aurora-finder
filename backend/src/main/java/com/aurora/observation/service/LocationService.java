package com.aurora.observation.service;

import com.aurora.observation.dto.Location;
import com.aurora.observation.provider.GeocodingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class LocationService {
    private final GeocodingProvider geocoding;
    private final Clock clock;
    private final int maxCacheEntries;
    private final Duration searchTtl;
    private final Duration locationTtl;
    private final Map<String, CacheEntry<List<Location>>> searches = new LinkedHashMap<>();
    private final Map<Long, CacheEntry<Location>> locations = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<List<Location>>> searchesInFlight = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Location>> locationsInFlight = new ConcurrentHashMap<>();

    public LocationService(GeocodingProvider geocoding, Clock clock,
                           @Value("${app.geocoding.cache.max-entries:256}") int maxCacheEntries,
                           @Value("${app.geocoding.cache.search-ttl:10m}") Duration searchTtl,
                           @Value("${app.geocoding.cache.location-ttl:1h}") Duration locationTtl) {
        if (maxCacheEntries < 1 || searchTtl.isNegative() || searchTtl.isZero()
                || locationTtl.isNegative() || locationTtl.isZero()) {
            throw new IllegalArgumentException("Geocoding cache limits and time-to-live values must be positive.");
        }
        this.geocoding = geocoding;
        this.clock = clock;
        this.maxCacheEntries = maxCacheEntries;
        this.searchTtl = searchTtl;
        this.locationTtl = locationTtl;
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

        return loadCoalesced(key, searchesInFlight, () -> {
            List<Location> secondCheck = read(searches, key);
            if (secondCheck != null) return secondCheck;
            List<Location> result = matchingProviderResults(cleaned);
            write(searches, key, result, searchTtl);
            for (Location location : result) write(locations, location.id(), location, locationTtl);
            return result;
        });
    }

    private List<Location> matchingProviderResults(String query) {
        List<Location> matches = filterMatches(query, geocoding.search(query));
        if (!matches.isEmpty()) return matches;

        for (String token : searchTokens(query)) {
            if (token.length() < 2) continue;
            matches = filterMatches(query, geocoding.search(token));
            if (!matches.isEmpty()) return matches;
        }
        return List.of();
    }

    private List<Location> filterMatches(String query, List<Location> candidates) {
        return candidates.stream().filter(location -> matchesQuery(query, location)).toList();
    }

    private boolean matchesQuery(String query, Location location) {
        List<String> queryTokens = searchTokens(query);
        if (queryTokens.isEmpty()) return false;
        List<String> locationTokens = java.util.Arrays.stream(new String[]{
                        location.name(), location.region(), location.subregion(), location.country()})
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> searchTokens(value).stream())
                .toList();
        return queryTokens.stream().allMatch(queryToken -> locationTokens.stream()
                .anyMatch(locationToken -> locationToken.startsWith(queryToken)));
    }

    private List<String> searchTokens(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        return normalized.isEmpty() ? List.of() : List.of(normalized.split("\\s+"));
    }

    public Location get(long id) {
        if (id <= 0) throw new InvalidLocationRequestException("Location ID must be positive.");
        Location cached = read(locations, id);
        if (cached != null) return cached;

        return loadCoalesced(id, locationsInFlight, () -> {
            Location secondCheck = read(locations, id);
            if (secondCheck != null) return secondCheck;
            Location location = geocoding.get(id).orElseThrow(() -> new LocationNotFoundException(id));
            write(locations, id, location, locationTtl);
            return location;
        });
    }

    private <K, V> V loadCoalesced(K key, Map<K, CompletableFuture<V>> requests, Supplier<V> loader) {
        CompletableFuture<V> pending = new CompletableFuture<>();
        CompletableFuture<V> existing = requests.putIfAbsent(key, pending);
        if (existing != null) return await(existing);

        try {
            V value = loader.get();
            pending.complete(value);
            return value;
        } catch (RuntimeException | Error failure) {
            pending.completeExceptionally(failure);
            throw failure;
        } finally {
            requests.remove(key, pending);
        }
    }

    private <V> V await(CompletableFuture<V> pending) {
        try {
            return pending.join();
        } catch (CompletionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (cause instanceof Error fatalFailure) throw fatalFailure;
            throw error;
        }
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
            if (cache.size() >= maxCacheEntries && !cache.containsKey(key)) {
                cache.remove(cache.keySet().iterator().next());
            }
            cache.put(key, new CacheEntry<>(value, clock.instant().plus(ttl)));
        }
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {}
}
