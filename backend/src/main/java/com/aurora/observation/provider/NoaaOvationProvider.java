package com.aurora.observation.provider;

import com.aurora.observation.dto.AuroraMapPoint;
import com.aurora.observation.dto.AuroraMapResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class NoaaOvationProvider implements OvationProvider {
    private static final String SOURCE_URL = "https://www.swpc.noaa.gov/products/aurora-30-minute-forecast";

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String dataUrl;
    private final Duration requestTimeout;

    public NoaaOvationProvider(HttpClient client, ObjectMapper mapper,
                               @Value("${app.ovation.data-url}") String dataUrl,
                               @Value("${app.ovation.request-timeout:10s}") Duration requestTimeout) {
        this.client = client;
        this.mapper = mapper;
        this.dataUrl = dataUrl;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public AuroraMapResponse latest() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(dataUrl)).timeout(requestTimeout)
                .header("Accept", "application/json").GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new ProviderUnavailableException(ProviderFailure.RATE_LIMITED, "NOAA returned HTTP 429");
            }
            if (response.statusCode() == 408 || response.statusCode() == 504) {
                throw new ProviderUnavailableException(ProviderFailure.TIMEOUT,
                        "NOAA returned HTTP " + response.statusCode());
            }
            if (response.statusCode() != 200) {
                throw new ProviderUnavailableException(ProviderFailure.UPSTREAM_ERROR,
                        "NOAA returned HTTP " + response.statusCode());
            }
            return parse(response.body());
        } catch (JacksonException error) {
            throw invalidResponse("NOAA returned invalid JSON", error);
        } catch (HttpTimeoutException error) {
            throw new ProviderUnavailableException(ProviderFailure.TIMEOUT, "NOAA request timed out", error);
        } catch (IOException error) {
            throw new ProviderUnavailableException(ProviderFailure.NETWORK_ERROR, "NOAA could not be reached", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException(ProviderFailure.INTERRUPTED, "NOAA request was interrupted", error);
        }
    }

    private AuroraMapResponse parse(String body) {
        JsonNode root = mapper.readTree(body);
        if (root == null || !root.isObject()
                || !"[Longitude, Latitude, Aurora]".equals(root.path("Data Format").asText())
                || !root.path("coordinates").isArray()) {
            throw invalidResponse("NOAA response has an unexpected grid format", null);
        }

        Instant observationTime = parseInstant(root.path("Observation Time"));
        Instant forecastTime = parseInstant(root.path("Forecast Time"));
        JsonNode coordinates = root.path("coordinates");
        if (coordinates.isEmpty()) throw invalidResponse("NOAA grid is empty", null);

        List<AuroraMapPoint> points = new ArrayList<>();
        for (JsonNode coordinate : coordinates) {
            if (!coordinate.isArray() || coordinate.size() != 3
                    || !coordinate.get(0).isIntegralNumber()
                    || !coordinate.get(1).isIntegralNumber()
                    || !coordinate.get(2).isIntegralNumber()) {
                throw invalidResponse("NOAA grid contains an invalid coordinate", null);
            }
            int longitude = coordinate.get(0).asInt();
            int latitude = coordinate.get(1).asInt();
            int auroraValue = coordinate.get(2).asInt();
            if (longitude < 0 || longitude > 359 || latitude < -90 || latitude > 90
                    || auroraValue < 0 || auroraValue > 100) {
                throw invalidResponse("NOAA grid contains an out-of-range value", null);
            }
            if (auroraValue > 0 && Math.abs(latitude) < 90) {
                double wrappedLongitude = longitude > 180 ? longitude - 360 : longitude;
                points.add(new AuroraMapPoint(wrappedLongitude, latitude, auroraValue));
            }
        }
        return new AuroraMapResponse(observationTime, forecastTime, SOURCE_URL, List.copyOf(points));
    }

    private Instant parseInstant(JsonNode node) {
        if (!node.isTextual()) throw invalidResponse("NOAA response is missing a timestamp", null);
        try {
            return Instant.parse(node.asText());
        } catch (DateTimeException error) {
            throw invalidResponse("NOAA response contains an invalid timestamp", error);
        }
    }

    private ProviderUnavailableException invalidResponse(String message, Throwable cause) {
        return cause == null
                ? new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, message)
                : new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, message, cause);
    }
}
