package com.aurora.observation.provider;

import com.aurora.observation.dto.KpIndexRecord;
import com.aurora.observation.dto.KpIndexResponse;
import com.aurora.observation.dto.KpIndexType;
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
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class NoaaKpIndexProvider implements KpIndexProvider {
    private static final String SOURCE_URL = "https://www.spaceweather.gov/products/planetary-k-index";

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String dataUrl;
    private final Duration requestTimeout;

    public NoaaKpIndexProvider(HttpClient client, ObjectMapper mapper, Clock clock,
                               @Value("${app.noaa-kp.data-url}") String dataUrl,
                               @Value("${app.noaa-kp.request-timeout:10s}") Duration requestTimeout) {
        this.client = client;
        this.mapper = mapper;
        this.clock = clock;
        this.dataUrl = dataUrl;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public KpIndexResponse latest() {
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
            throw invalidResponse("NOAA returned invalid Kp JSON", error);
        } catch (HttpTimeoutException error) {
            throw new ProviderUnavailableException(ProviderFailure.TIMEOUT, "NOAA Kp request timed out", error);
        } catch (IOException error) {
            throw new ProviderUnavailableException(ProviderFailure.NETWORK_ERROR,
                    "NOAA Kp data could not be reached", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException(ProviderFailure.INTERRUPTED,
                    "NOAA Kp request was interrupted", error);
        }
    }

    private KpIndexResponse parse(String body) {
        JsonNode root = mapper.readTree(body);
        if (root == null || !root.isArray() || root.isEmpty()) {
            throw invalidResponse("NOAA Kp response is empty or not an array", null);
        }
        List<KpIndexRecord> records = new ArrayList<>();
        for (JsonNode item : root) {
            if (!item.isObject() || !item.path("kp").isNumber()) {
                throw invalidResponse("NOAA Kp record is missing a numeric index", null);
            }
            double kp = item.path("kp").doubleValue();
            if (!Double.isFinite(kp) || kp < 0 || kp > 9) {
                throw invalidResponse("NOAA Kp record has an out-of-range index", null);
            }
            JsonNode scale = item.path("noaa_scale");
            if (!scale.isMissingNode() && !scale.isNull() && !scale.isTextual()) {
                throw invalidResponse("NOAA Kp record has an invalid scale", null);
            }
            Instant periodStart = parseUtcTimestamp(item.path("time_tag"));
            KpIndexType type = parseType(item.path("observed"));
            records.add(new KpIndexRecord(periodStart, kp, type,
                    scale.isTextual() ? scale.asText() : null));
        }
        return new KpIndexResponse(clock.instant(), SOURCE_URL, List.copyOf(records));
    }

    private Instant parseUtcTimestamp(JsonNode timestamp) {
        if (!timestamp.isTextual()) throw invalidResponse("NOAA Kp record is missing its timestamp", null);
        try {
            // NOAA time_tag values use UTC synoptic intervals but omit the UTC suffix.
            return LocalDateTime.parse(timestamp.asText()).toInstant(ZoneOffset.UTC);
        } catch (DateTimeException error) {
            throw invalidResponse("NOAA Kp record has an invalid timestamp", error);
        }
    }

    private KpIndexType parseType(JsonNode type) {
        if (!type.isTextual()) throw invalidResponse("NOAA Kp record is missing its type", null);
        try {
            return KpIndexType.valueOf(type.asText().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw invalidResponse("NOAA Kp record has an unknown type", error);
        }
    }

    private ProviderUnavailableException invalidResponse(String message, Throwable cause) {
        return cause == null
                ? new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, message)
                : new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, message, cause);
    }
}
