package com.aurora.observation.service;

import com.aurora.observation.dto.Location;
import com.aurora.observation.provider.GeocodingProvider;
import com.aurora.observation.provider.OpenMeteoGeocodingProvider;
import com.aurora.observation.provider.ProviderUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class LocationService {
    // Longest first, so 内蒙古自治区 loses 自治区 rather than only 区.
    private static final List<String> CHINESE_ADMINISTRATIVE_SUFFIXES =
            List.of("特别行政区", "自治区", "自治州", "自治县", "地区", "省", "市", "县", "区", "州", "盟", "旗");

    private final GeocodingProvider geocoding;
    private final Clock clock;
    private final int maxCacheEntries;
    private final Duration searchTtl;
    private final Duration locationTtl;
    private final Map<String, CacheEntry<List<Location>>> searches = new LinkedHashMap<>();
    private final Map<Long, CacheEntry<Location>> locations = new LinkedHashMap<>();
    private final Map<Long, CacheEntry<Location>> chineseLocations = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<List<Location>>> searchesInFlight = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Location>> locationsInFlight = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Location>> chineseLocationsInFlight = new ConcurrentHashMap<>();

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

    /** Names follow the language of the query, so the geocoder's answer can be matched against what was typed. */
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
            // Localized names stay with their search; ID lookups keep the provider's default English names.
            if (!"zh".equals(OpenMeteoGeocodingProvider.searchLanguage(cleaned))) {
                for (Location location : result) write(locations, location.id(), location, locationTtl);
            }
            return result;
        });
    }

    /** The same matches, named in the language the page is showing. */
    public List<Location> search(String query, String language) {
        List<Location> matches = search(query);
        if (language == null) return matches;
        String wanted = supportedLanguage(language);
        String cleaned = query.strip();
        if (matches.isEmpty() || wanted.equals(OpenMeteoGeocodingProvider.searchLanguage(cleaned))) return matches;

        String key = wanted + "|" + cleaned.toLowerCase(Locale.ROOT);
        List<Location> cached = read(searches, key);
        if (cached != null) return cached;
        return loadCoalesced(key, searchesInFlight, () -> {
            List<Location> result;
            try {
                result = "en".equals(wanted) ? inEnglish(matches) : inChinese(matches);
            } catch (ProviderUnavailableException unavailable) {
                return matches;
            }
            write(searches, key, result, searchTtl);
            return result;
        });
    }

    /** The geocoder finds nothing for a Chinese name when asked to answer in English, so these go by ID. */
    private List<Location> inEnglish(List<Location> matches) {
        return matches.stream().map(match -> {
            try {
                return get(match.id());
            } catch (LocationNotFoundException gone) {
                return match;
            }
        }).toList();
    }

    private List<Location> inChinese(List<Location> matches) {
        Map<Long, Location> named = new LinkedHashMap<>();
        geocoding.search(matches.getFirst().name(), "zh").forEach(location -> named.put(location.id(), location));
        return matches.stream()
                .map(match -> named.containsKey(match.id()) ? inLanguage(named.get(match.id()), "zh") : match)
                .toList();
    }

    /** The place's names in the given language, for showing a place that was found or saved in another one. */
    public Location get(long id, String language) {
        if ("en".equals(supportedLanguage(language))) return get(id);
        if (id <= 0) throw new InvalidLocationRequestException("Location ID must be positive.");
        Location cached = read(chineseLocations, id);
        if (cached != null) return cached;

        return loadCoalesced(id, chineseLocationsInFlight, () -> {
            Location secondCheck = read(chineseLocations, id);
            if (secondCheck != null) return secondCheck;
            Location location = geocoding.get(id, "zh").map(found -> inLanguage(found, "zh"))
                    .orElseThrow(() -> new LocationNotFoundException(id));
            write(chineseLocations, id, location, locationTtl);
            return location;
        });
    }

    private String supportedLanguage(String language) {
        if ("en".equals(language) || "zh".equals(language)) return language;
        throw new InvalidLocationRequestException("Language must be en or zh.");
    }

    /** The geocoder leaves many Chinese regions in English and gives some cities a Traditional or a rarer name. */
    private Location inLanguage(Location location, String language) {
        if (!"zh".equals(language)) return location;
        ChineseLocationAliases.Alias alias = ChineseLocationAliases.forLocation(location.id());
        return new Location(location.id(), alias == null ? location.name() : alias.name(),
                hanOnly(location.region()), hanOnly(location.subregion()),
                alias == null ? location.country() : alias.country(),
                location.latitude(), location.longitude(), location.timezone());
    }

    private String hanOnly(String value) {
        return value != null && !value.isBlank() && isHan(value) ? value : "";
    }

    private List<Location> matchingProviderResults(String query) {
        Set<String> terms = new LinkedHashSet<>();
        terms.add(query);
        terms.addAll(searchTokens(query));
        // The geocoder finds 北京 but not 北京市, so a suffixed Chinese name is retried without the suffix.
        for (String term : List.copyOf(terms)) {
            String withoutSuffix = withoutChineseAdministrativeSuffix(term);
            if (withoutSuffix != null) terms.add(withoutSuffix);
        }

        Map<Long, Location> matches = new LinkedHashMap<>();
        aliasMatch(query, terms).ifPresent(location -> matches.put(location.id(), location));
        for (String term : terms) {
            if (term.length() < 2) continue;
            List<Location> found = filterMatches(query, geocoding.search(term));
            String language = OpenMeteoGeocodingProvider.searchLanguage(query);
            found.forEach(location -> matches.putIfAbsent(location.id(), inLanguage(location, language)));
            if (!found.isEmpty()) break;
        }
        return List.copyOf(matches.values());
    }

    /** Comes first because it is the place people usually mean; other places with that name still follow. */
    private Optional<Location> aliasMatch(String query, Set<String> terms) {
        for (String term : terms) {
            ChineseLocationAliases.Alias alias = ChineseLocationAliases.find(term);
            if (alias == null) continue;
            Location place;
            try {
                place = get(alias.locationId());
            } catch (LocationNotFoundException gone) {
                return Optional.empty();
            }
            Location named = new Location(place.id(), term, "", "", alias.country(),
                    place.latitude(), place.longitude(), place.timezone());
            return Optional.of(named).filter(location -> matchesQuery(query, location));
        }
        return Optional.empty();
    }

    private String withoutChineseAdministrativeSuffix(String term) {
        if (!isHan(term)) return null;
        for (String suffix : CHINESE_ADMINISTRATIVE_SUFFIXES) {
            if (term.endsWith(suffix) && term.length() - suffix.length() >= 2) {
                return term.substring(0, term.length() - suffix.length());
            }
        }
        return null;
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
                .anyMatch(locationToken -> locationToken.startsWith(queryToken)
                        // Chinese names are often typed with a suffix such as 市 or 省 that the result omits.
                        || isHan(locationToken) && locationToken.length() >= 2 && queryToken.startsWith(locationToken)));
    }

    private boolean isHan(String value) {
        return value.codePoints().allMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN);
    }

    private List<String> searchTokens(String value) {
        String normalized = foldLettersWithoutDecomposition(Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT))
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        return normalized.isEmpty() ? List.of() : List.of(normalized.split("\\s+"));
    }

    /** NFD leaves letters such as ø and æ whole, so "Tromso" would never match "Tromsø" without this. */
    private String foldLettersWithoutDecomposition(String value) {
        StringBuilder folded = new StringBuilder(value.length());
        value.codePoints().forEach(point -> folded.append(switch (point) {
            case 'ø' -> "o";
            case 'æ' -> "ae";
            case 'œ' -> "oe";
            case 'ß' -> "ss";
            case 'ł' -> "l";
            case 'đ', 'ð' -> "d";
            case 'þ' -> "th";
            case 'ı' -> "i";
            default -> Character.toString(point);
        }));
        return folded.toString();
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
