package com.aurora.observation.provider;

import com.aurora.observation.dto.Location;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class OpenMeteoGeocodingProvider implements GeocodingProvider {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final boolean enabled;

    public OpenMeteoGeocodingProvider(ObjectMapper mapper,
                                      @Value("${app.geocoding.base-url}") String baseUrl,
                                      @Value("${app.geocoding.enabled}") boolean enabled) {
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    @Override
    public List<Location> search(String query) {
        if (!enabled) throw new ProviderUnavailableException(ProviderFailure.DISABLED, "Geocoding is disabled");
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        JsonNode response = request(baseUrl + "/search?name=" + encoded + "&count=10&language=en&format=json", false)
                .orElseThrow();
        if (!response.isObject() || response.path("error").asBoolean(false)) {
            throw invalidResponse("Search response is not a successful object");
        }
        JsonNode results = response.get("results");
        if (results == null) {
            if (response.path("generationtime_ms").isNumber()) return List.of();
            throw invalidResponse("Search response is missing results and metadata");
        }
        if (!results.isArray()) throw invalidResponse("Search results are not an array");
        List<Location> locations = new ArrayList<>();
        for (JsonNode result : results) {
            locations.add(toLocation(result));
        }
        return locations;
    }

    @Override
    public Optional<Location> get(long id) {
        if (!enabled) throw new ProviderUnavailableException(ProviderFailure.DISABLED, "Geocoding is disabled");
        return request(baseUrl + "/get?id=" + id, true).map(node -> {
            Location location = toLocation(node);
            if (location.id() != id) throw invalidResponse("Location ID does not match the requested ID");
            return location;
        });
    }

    private Optional<JsonNode> request(String url, boolean allowNotFound) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8)).header("Accept", "application/json").GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 404 && allowNotFound) return Optional.empty();
            if (status == 429) throw new ProviderUnavailableException(ProviderFailure.RATE_LIMITED, "Geocoding returned HTTP 429");
            if (status == 408 || status == 504) throw new ProviderUnavailableException(ProviderFailure.TIMEOUT, "Geocoding returned HTTP " + status);
            if (status != 200) throw new ProviderUnavailableException(ProviderFailure.UPSTREAM_ERROR, "Geocoding returned HTTP " + status);
            JsonNode parsed = mapper.readTree(response.body());
            if (parsed == null) throw invalidResponse("Geocoding returned an empty response");
            return Optional.of(parsed);
        } catch (JacksonException e) {
            throw new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, "Geocoding returned invalid JSON", e);
        } catch (HttpTimeoutException e) {
            throw new ProviderUnavailableException(ProviderFailure.TIMEOUT, "Geocoding timed out", e);
        } catch (IOException e) {
            throw new ProviderUnavailableException(ProviderFailure.NETWORK_ERROR, "Geocoding could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException(ProviderFailure.INTERRUPTED, "Geocoding was interrupted", e);
        }
    }

    private Location toLocation(JsonNode node) {
        if (!node.isObject() || !node.path("id").isIntegralNumber() || !node.path("id").canConvertToLong()
                || node.path("id").asLong() <= 0 || !node.path("name").isTextual()
                || node.path("name").asText().isBlank() || !node.path("latitude").isNumber()
                || !node.path("longitude").isNumber() || !node.path("timezone").isTextual()) {
            throw invalidResponse("Location has missing or incorrectly typed fields");
        }
        double latitude = node.path("latitude").asDouble();
        double longitude = node.path("longitude").asDouble();
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw invalidResponse("Location coordinates are outside valid ranges");
        }
        String timezone = node.path("timezone").asText();
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, "Location has an invalid time zone", e);
        }
        return new Location(node.path("id").asLong(), node.path("name").asText(),
                node.path("admin1").asText(""), node.path("country").asText(""),
                latitude, longitude, timezone);
    }

    private ProviderUnavailableException invalidResponse(String message) {
        return new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, message);
    }
}
