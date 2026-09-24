package com.aurora.observation.provider;

import com.aurora.observation.dto.WeatherCloudPoint;
import com.aurora.observation.dto.WeatherForecastResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

@Component
public class MetNoWeatherProvider implements WeatherProvider {
    private static final Logger log = LoggerFactory.getLogger(MetNoWeatherProvider.class);
    private static final String SOURCE = "https://api.met.no/weatherapi/locationforecast/2.0/compact";
    private static final String ATTRIBUTION = "https://api.met.no/";

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String dataUrl;
    private final String userAgent;
    private final Duration timeout;
    private final Duration defaultTtl;
    private final int maxEntries;
    private final Map<CoordinateKey, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<CoordinateKey, CompletableFuture<WeatherForecastResponse>> inFlight = new ConcurrentHashMap<>();

    public MetNoWeatherProvider(HttpClient client, ObjectMapper mapper, Clock clock,
                                @Value("${app.metno.base-url}") String dataUrl,
                                @Value("${app.metno.user-agent}") String userAgent,
                                @Value("${app.metno.request-timeout:10s}") Duration timeout,
                                @Value("${app.metno.cache.default-ttl:15m}") Duration defaultTtl,
                                @Value("${app.metno.cache.max-entries:256}") int maxEntries) {
        if (timeout.isNegative() || timeout.isZero() || defaultTtl.isNegative() || defaultTtl.isZero() || maxEntries < 1) {
            throw new IllegalArgumentException("MET Norway timeout and cache limits must be positive.");
        }
        if (userAgent == null || userAgent.isBlank()) throw new IllegalArgumentException("MET Norway User-Agent is required.");
        this.client = client;
        this.mapper = mapper;
        this.clock = clock;
        this.dataUrl = dataUrl;
        this.userAgent = userAgent;
        this.timeout = timeout;
        this.defaultTtl = defaultTtl;
        this.maxEntries = maxEntries;
    }

    @Override
    public WeatherForecastResponse forecast(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Coordinates must be within latitude -90..90 and longitude -180..180.");
        }
        double requestLatitude = truncateCoordinate(latitude);
        double requestLongitude = truncateCoordinate(longitude);
        CoordinateKey key = new CoordinateKey(requestLatitude, requestLongitude);
        Instant now = clock.instant();
        CacheEntry previous = read(key);
        if (previous != null && now.isBefore(previous.response().expiresAt())) return previous.response();

        CompletableFuture<WeatherForecastResponse> pending = new CompletableFuture<>();
        CompletableFuture<WeatherForecastResponse> existing = inFlight.putIfAbsent(key, pending);
        if (existing != null) return await(existing);
        try {
            CacheEntry secondCheck = read(key);
            Instant secondNow = clock.instant();
            if (secondCheck != null && secondNow.isBefore(secondCheck.response().expiresAt())) {
                pending.complete(secondCheck.response());
                return secondCheck.response();
            }
            WeatherForecastResponse result = fetch(key, requestLatitude, requestLongitude, secondCheck, secondNow);
            pending.complete(result);
            return result;
        } catch (RuntimeException | Error error) {
            pending.completeExceptionally(error);
            throw error;
        } finally {
            inFlight.remove(key, pending);
        }
    }

    private WeatherForecastResponse fetch(CoordinateKey key, double requestLatitude, double requestLongitude,
                                          CacheEntry previous, Instant now) {

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(dataUrl + "?lat=" + requestLatitude + "&lon=" + requestLongitude))
                .timeout(timeout).header("User-Agent", userAgent)
                .header("Accept", "application/json").header("Accept-Encoding", "gzip, deflate").GET();
        if (previous != null) {
            if (previous.etag() != null) builder.header("If-None-Match", previous.etag());
            if (previous.lastModified() != null) builder.header("If-Modified-Since", previous.lastModified());
        }

        try {
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status == 429) throw new ProviderUnavailableException(ProviderFailure.RATE_LIMITED, "MET Norway returned HTTP 429");
            if (status == 403) throw new ProviderUnavailableException(ProviderFailure.FORBIDDEN,
                    "MET Norway rejected the request; verify the identifying User-Agent.");
            if (status == 408 || status == 504) throw new ProviderUnavailableException(ProviderFailure.TIMEOUT,
                    "MET Norway returned HTTP " + status);
            if (status == 304 && previous != null) {
                CacheEntry refreshed = refreshed(previous, response, now);
                put(key, refreshed);
                return refreshed.response();
            }
            if (status != 200 && status != 203) throw new ProviderUnavailableException(ProviderFailure.UPSTREAM_ERROR,
                    "MET Norway returned HTTP " + status);
            if (status == 203) log.warn("MET Norway Locationforecast API is marked deprecated (HTTP 203).");
            byte[] body = decodeBody(response.body(), response.headers().firstValue("Content-Encoding").orElse(""));
            WeatherForecastResponse parsed = parse(body, requestLatitude, requestLongitude, now,
                    expiry(response, now.plus(defaultTtl)));
            CacheEntry entry = new CacheEntry(parsed, response.headers().firstValue("ETag").orElse(null),
                    response.headers().firstValue("Last-Modified").orElse(null));
            put(key, entry);
            return parsed;
        } catch (JacksonException error) {
            throw invalidResponse("MET Norway returned invalid forecast JSON", error);
        } catch (HttpTimeoutException error) {
            throw new ProviderUnavailableException(ProviderFailure.TIMEOUT, "MET Norway request timed out", error);
        } catch (IOException error) {
            throw new ProviderUnavailableException(ProviderFailure.NETWORK_ERROR, "MET Norway could not be reached", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException(ProviderFailure.INTERRUPTED, "MET Norway request was interrupted", error);
        }
    }

    private CacheEntry read(CoordinateKey key) {
        synchronized (cache) { return cache.get(key); }
    }

    private WeatherForecastResponse await(CompletableFuture<WeatherForecastResponse> pending) {
        try { return pending.join(); }
        catch (CompletionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (cause instanceof Error fatalFailure) throw fatalFailure;
            throw error;
        }
    }

    private WeatherForecastResponse parse(byte[] bytes, double latitude, double longitude, Instant retrievedAt, Instant expiresAt) {
        JsonNode root = mapper.readTree(bytes);
        JsonNode series = root == null ? null : root.path("properties").path("timeseries");
        if (series == null || !series.isArray() || series.isEmpty()) {
            throw invalidResponse("MET Norway forecast has no time series", null);
        }
        List<WeatherCloudPoint> points = new ArrayList<>();
        for (JsonNode item : series) {
            Instant validAt = parseTimestamp(item.path("time"));
            JsonNode cloud = item.path("data").path("instant").path("details").path("cloud_area_fraction");
            Double cover = null;
            if (!cloud.isMissingNode() && !cloud.isNull()) {
                if (!cloud.isNumber() || !Double.isFinite(cloud.doubleValue()) || cloud.doubleValue() < 0 || cloud.doubleValue() > 100) {
                    throw invalidResponse("MET Norway returned an invalid cloud cover value", null);
                }
                cover = cloud.doubleValue();
            }
            if (!points.isEmpty() && !validAt.isAfter(points.getLast().validAt())) {
                throw invalidResponse("MET Norway forecast times are not strictly increasing", null);
            }
            points.add(new WeatherCloudPoint(validAt, cover));
        }
        return new WeatherForecastResponse(retrievedAt, expiresAt, SOURCE, latitude, longitude, List.copyOf(points));
    }

    private Instant parseTimestamp(JsonNode node) {
        if (!node.isTextual()) throw invalidResponse("MET Norway forecast is missing a timestamp", null);
        try {
            return OffsetDateTime.parse(node.asText()).toInstant();
        } catch (DateTimeParseException error) {
            throw invalidResponse("MET Norway forecast contains an invalid timestamp", error);
        }
    }

    private CacheEntry refreshed(CacheEntry previous, HttpResponse<?> response, Instant now) {
        WeatherForecastResponse old = previous.response();
        WeatherForecastResponse updated = new WeatherForecastResponse(old.retrievedAt(), expiry(response, now.plus(defaultTtl)),
                old.source(), old.requestedLatitude(), old.requestedLongitude(), old.cloudForecast());
        return new CacheEntry(updated,
                response.headers().firstValue("ETag").orElse(previous.etag()),
                response.headers().firstValue("Last-Modified").orElse(previous.lastModified()));
    }

    private Instant expiry(HttpResponse<?> response, Instant fallback) {
        return response.headers().firstValue("Expires").flatMap(value -> {
            try { return java.util.Optional.of(OffsetDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()); }
            catch (DateTimeParseException error) { return java.util.Optional.empty(); }
        }).orElse(fallback);
    }

    private byte[] decodeBody(byte[] body, String encoding) throws IOException {
        try {
            try (var input = switch (encoding.toLowerCase(java.util.Locale.ROOT)) {
                case "gzip" -> new GZIPInputStream(new ByteArrayInputStream(body));
                case "deflate" -> new InflaterInputStream(new ByteArrayInputStream(body));
                case "", "identity" -> new ByteArrayInputStream(body);
                default -> throw invalidResponse("MET Norway returned an unsupported content encoding", null);
            }) {
                return input.readAllBytes();
            }
        } catch (IOException error) {
            throw invalidResponse("MET Norway returned a corrupted compressed response", error);
        }
    }

    private double truncateCoordinate(double coordinate) {
        return BigDecimal.valueOf(coordinate).setScale(4, RoundingMode.DOWN).doubleValue();
    }

    private void put(CoordinateKey key, CacheEntry entry) {
        synchronized (cache) {
            cache.put(key, entry);
            while (cache.size() > maxEntries) cache.remove(cache.keySet().iterator().next());
        }
    }

    private ProviderUnavailableException invalidResponse(String message, Throwable cause) {
        return cause == null ? new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, message)
                : new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, message, cause);
    }

    private record CoordinateKey(double latitude, double longitude) {}
    private record CacheEntry(WeatherForecastResponse response, String etag, String lastModified) {}
}
