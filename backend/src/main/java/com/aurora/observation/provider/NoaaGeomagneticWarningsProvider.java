package com.aurora.observation.provider;

import com.aurora.observation.dto.GeomagneticWarning;
import com.aurora.observation.dto.GeomagneticWarningsResponse;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NoaaGeomagneticWarningsProvider {
    private static final String SOURCE_URL = "https://services.swpc.noaa.gov/products/alerts.json";
    private static final Pattern VALID_FROM = Pattern.compile("(?m)^Valid From:\\s*(\\d{4}\\s+[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{4})\\s+UTC");
    private static final Pattern VALID_TO = Pattern.compile("(?m)^(?:Valid To|Now Valid Until):\\s*(\\d{4}\\s+[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{4})\\s+UTC");
    private static final Pattern K_INDEX = Pattern.compile("K-index of (4|5) expected", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOAA_SCALE = Pattern.compile("(?im)^Noaa Scale:\\s*(G[1-5]\\s*-\\s*[^\\r\\n]+)");
    private static final DateTimeFormatter NOAA_TIME = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("uuuu MMM d HHmm").toFormatter(Locale.US).withResolverStyle(ResolverStyle.STRICT);
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String dataUrl;
    private final Duration requestTimeout;

    public NoaaGeomagneticWarningsProvider(HttpClient client, ObjectMapper mapper, Clock clock,
            @Value("${app.noaa-alerts.data-url}") String dataUrl,
            @Value("${app.noaa-alerts.request-timeout:10s}") Duration requestTimeout) {
        this.client = client;
        this.mapper = mapper;
        this.clock = clock;
        this.dataUrl = dataUrl;
        this.requestTimeout = requestTimeout;
    }

    public GeomagneticWarningsResponse latest() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(dataUrl)).timeout(requestTimeout)
                .header("Accept", "application/json").GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw new ProviderUnavailableException(ProviderFailure.RATE_LIMITED, "NOAA returned HTTP 429");
            if (response.statusCode() == 408 || response.statusCode() == 504) throw new ProviderUnavailableException(ProviderFailure.TIMEOUT, "NOAA returned HTTP " + response.statusCode());
            if (response.statusCode() != 200) throw new ProviderUnavailableException(ProviderFailure.UPSTREAM_ERROR, "NOAA returned HTTP " + response.statusCode());
            return parse(response.body());
        } catch (JacksonException error) {
            throw invalid("NOAA alerts returned invalid JSON", error);
        } catch (HttpTimeoutException error) {
            throw new ProviderUnavailableException(ProviderFailure.TIMEOUT, "NOAA alerts request timed out", error);
        } catch (IOException error) {
            throw new ProviderUnavailableException(ProviderFailure.NETWORK_ERROR, "NOAA alerts could not be reached", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException(ProviderFailure.INTERRUPTED, "NOAA alerts request was interrupted", error);
        }
    }

    GeomagneticWarningsResponse parse(String body) {
        JsonNode root = mapper.readTree(body);
        if (root == null || !root.isArray()) throw invalid("NOAA alerts response is not an array", null);
        Instant now = clock.instant();
        List<GeomagneticWarning> warnings = new ArrayList<>();
        for (JsonNode item : root) {
            if (!item.isObject()) throw invalid("NOAA alerts contain an invalid item", null);
            JsonNode productNode = item.path("product_id");
            JsonNode messageNode = item.path("message");
            if (!productNode.isTextual() || !messageNode.isTextual()) throw invalid("NOAA alert is missing its product or message", null);
            String productId = productNode.asText();
            if (!productId.matches("K0[45]W")) continue;
            String message = messageNode.asText();
            Matcher kMatcher = K_INDEX.matcher(message);
            Matcher startMatcher = VALID_FROM.matcher(message);
            Matcher endMatcher = VALID_TO.matcher(message);
            if (!kMatcher.find() || !startMatcher.find() || !endMatcher.find()) continue;
            try {
                int kIndex = Integer.parseInt(kMatcher.group(1));
                Instant validFrom = LocalDateTime.parse(startMatcher.group(1), NOAA_TIME).toInstant(ZoneOffset.UTC);
                Instant validTo = LocalDateTime.parse(endMatcher.group(1), NOAA_TIME).toInstant(ZoneOffset.UTC);
                if (!validFrom.isBefore(validTo)) throw invalid("NOAA warning has an invalid validity window", null);
                if (!now.isBefore(validTo)) continue;
                Matcher scaleMatcher = NOAA_SCALE.matcher(message);
                String scale = scaleMatcher.find() ? scaleMatcher.group(1).trim() : null;
                warnings.add(new GeomagneticWarning(productId, kIndex, scale, validFrom, validTo));
            } catch (RuntimeException error) {
                if (error instanceof ProviderUnavailableException unavailable) throw unavailable;
                throw invalid("NOAA warning has an invalid time", error);
            }
        }
        return new GeomagneticWarningsResponse(now, SOURCE_URL, List.copyOf(warnings));
    }

    private ProviderUnavailableException invalid(String message, Throwable cause) {
        return cause == null ? new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, message)
                : new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, message, cause);
    }
}
