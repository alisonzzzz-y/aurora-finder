package com.aurora.observation.provider;

import com.aurora.observation.dto.GeomagneticWarning;
import com.aurora.observation.dto.GeomagneticStormWatchDay;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
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
    private static final Pattern WATCH_DAY = Pattern.compile("(?i)([A-Z]{3})\\s+(\\d{1,2}):\\s*(G[1-5]|None)");
    private static final DateTimeFormatter ISSUE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final DateTimeFormatter WATCH_DATE = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("uuuu MMM d").toFormatter(Locale.US).withResolverStyle(ResolverStyle.STRICT);
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
        JsonNode latestWatch = null;
        Instant latestWatchIssue = Instant.MIN;
        for (JsonNode item : root) {
            if (!item.isObject()) throw invalid("NOAA alerts contain an invalid item", null);
            JsonNode productNode = item.path("product_id");
            JsonNode messageNode = item.path("message");
            if (!productNode.isTextual() || !messageNode.isTextual()) throw invalid("NOAA alert is missing its product or message", null);
            String productId = productNode.asText();
            String message = messageNode.asText();
            if (productId.matches("A[2-6]0F")) {
                JsonNode issueNode = item.path("issue_datetime");
                if (!issueNode.isTextual()) throw invalid("NOAA storm watch is missing its issue time", null);
                try {
                    Instant issuedAt = LocalDateTime.parse(issueNode.asText(), ISSUE_TIME).toInstant(ZoneOffset.UTC);
                    if (issuedAt.isAfter(latestWatchIssue)) {
                        latestWatchIssue = issuedAt;
                        latestWatch = item;
                    }
                } catch (RuntimeException error) {
                    throw invalid("NOAA storm watch has an invalid issue time", error);
                }
            }
            if (!productId.matches("K0[45]W")) continue;
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
        return new GeomagneticWarningsResponse(now, SOURCE_URL, List.copyOf(warnings),
                parseLatestWatch(latestWatch, now));
    }

    private List<GeomagneticStormWatchDay> parseLatestWatch(JsonNode watch, Instant now) {
        if (watch == null) return List.of();
        String message = watch.path("message").asText();
        if (message.toUpperCase(Locale.ROOT).contains("CANCEL WATCH")) return List.of();
        JsonNode issueNode = watch.path("issue_datetime");
        LocalDate issueDate = LocalDateTime.parse(issueNode.asText(), ISSUE_TIME).toLocalDate();
        Matcher matcher = WATCH_DAY.matcher(message);
        List<GeomagneticStormWatchDay> days = new ArrayList<>();
        LocalDate todayUtc = now.atZone(ZoneOffset.UTC).toLocalDate();
        while (matcher.find()) {
            try {
                int year = issueDate.getYear();
                LocalDate date = LocalDate.parse(year + " " + matcher.group(1) + " " + matcher.group(2), WATCH_DATE);
                if (date.isBefore(issueDate.minusMonths(6))) date = date.plusYears(1);
                else if (date.isAfter(issueDate.plusMonths(6))) date = date.minusYears(1);
                if (date.isBefore(todayUtc)) continue;
                String category = matcher.group(3);
                days.add(new GeomagneticStormWatchDay(date, category.equalsIgnoreCase("None") ? null : category));
            } catch (RuntimeException error) {
                throw invalid("NOAA storm watch contains an invalid forecast day", error);
            }
        }
        return days.stream().distinct().sorted(Comparator.comparing(GeomagneticStormWatchDay::date)).toList();
    }

    private ProviderUnavailableException invalid(String message, Throwable cause) {
        return cause == null ? new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, message)
                : new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, message, cause);
    }
}
