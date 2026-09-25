package com.aurora.observation.provider;

import com.aurora.observation.dto.GeomagneticStormDay;
import com.aurora.observation.dto.GeomagneticStormForecastResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
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
public class NoaaGeomagneticStormForecastProvider implements GeomagneticStormForecastProvider {
    private static final String SOURCE_URL = "https://services.swpc.noaa.gov/text/3-day-geomag-forecast.txt";
    private static final Pattern ISSUED = Pattern.compile("^:Issued:\\s+(\\d{4})\\s+([A-Za-z]{3})\\s+(\\d{1,2})\\s+(\\d{4})\\s+UTC$", Pattern.MULTILINE);
    private static final Pattern RANGE = Pattern.compile("NOAA Geomagnetic Activity Probabilities\\s+(\\d{1,2})\\s+([A-Za-z]{3})-(\\d{1,2})\\s+([A-Za-z]{3})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROBABILITY = Pattern.compile("^(Active|Minor storm|Moderate storm|Strong-Extreme storm)\\s+(\\d{1,3})/(\\d{1,3})/(\\d{1,3})\\s*$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter ISSUED_DATE = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("uuuu MMM d HHmm").toFormatter(Locale.US).withResolverStyle(ResolverStyle.STRICT);
    private final HttpClient client;
    private final Clock clock;
    private final String dataUrl;
    private final Duration requestTimeout;

    public NoaaGeomagneticStormForecastProvider(HttpClient client, Clock clock,
            @Value("${app.noaa-geomag.data-url}") String dataUrl,
            @Value("${app.noaa-geomag.request-timeout:10s}") Duration requestTimeout) {
        this.client = client;
        this.clock = clock;
        this.dataUrl = dataUrl;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public GeomagneticStormForecastResponse latest() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(dataUrl)).timeout(requestTimeout)
                .header("Accept", "text/plain").GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw new ProviderUnavailableException(ProviderFailure.RATE_LIMITED, "NOAA returned HTTP 429");
            if (response.statusCode() == 408 || response.statusCode() == 504) throw new ProviderUnavailableException(ProviderFailure.TIMEOUT, "NOAA returned HTTP " + response.statusCode());
            if (response.statusCode() != 200) throw new ProviderUnavailableException(ProviderFailure.UPSTREAM_ERROR, "NOAA returned HTTP " + response.statusCode());
            return parse(response.body());
        } catch (HttpTimeoutException error) {
            throw new ProviderUnavailableException(ProviderFailure.TIMEOUT, "NOAA geomagnetic forecast timed out", error);
        } catch (IOException error) {
            throw new ProviderUnavailableException(ProviderFailure.NETWORK_ERROR, "NOAA geomagnetic forecast could not be reached", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException(ProviderFailure.INTERRUPTED, "NOAA geomagnetic forecast request was interrupted", error);
        }
    }

    GeomagneticStormForecastResponse parse(String body) {
        try {
            Matcher issuedMatcher = ISSUED.matcher(body);
            Matcher rangeMatcher = RANGE.matcher(body);
            if (!issuedMatcher.find() || !rangeMatcher.find()) throw invalid("NOAA geomagnetic forecast is missing its issue time or date range", null);
            LocalDateTime issuedDateTime = LocalDateTime.parse(issuedMatcher.group(1) + " " + issuedMatcher.group(2) + " " + issuedMatcher.group(3) + " " + issuedMatcher.group(4), ISSUED_DATE);
            Instant issuedAt = issuedDateTime.toInstant(ZoneOffset.UTC);
            int year = issuedDateTime.getYear();
            Month startMonth = Month.from(DateTimeFormatter.ofPattern("MMM", Locale.US).parse(rangeMatcher.group(2)));
            Month endMonth = Month.from(DateTimeFormatter.ofPattern("MMM", Locale.US).parse(rangeMatcher.group(4)));
            LocalDate start = LocalDate.of(year, startMonth, Integer.parseInt(rangeMatcher.group(1)));
            if (start.isBefore(issuedDateTime.toLocalDate().minusDays(2))) start = start.plusYears(1);
            LocalDate end = LocalDate.of(start.getYear(), endMonth, Integer.parseInt(rangeMatcher.group(3)));
            if (end.isBefore(start)) end = end.plusYears(1);
            if (Duration.between(start.atStartOfDay().toInstant(ZoneOffset.UTC), end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)).toDays() != 3) throw invalid("NOAA geomagnetic forecast has an unexpected date range", null);
            int count = 3;
            int[][] values = new int[4][count];
            boolean[] found = new boolean[4];
            Matcher probabilityMatcher = PROBABILITY.matcher(body);
            while (probabilityMatcher.find()) {
                int row = switch (probabilityMatcher.group(1).toLowerCase(Locale.ROOT)) {
                    case "active" -> 0;
                    case "minor storm" -> 1;
                    case "moderate storm" -> 2;
                    case "strong-extreme storm" -> 3;
                    default -> throw invalid("NOAA geomagnetic forecast has an unknown category", null);
                };
                if (found[row]) throw invalid("NOAA geomagnetic forecast repeats a probability category", null);
                for (int i = 0; i < count; i++) {
                    values[row][i] = Integer.parseInt(probabilityMatcher.group(i + 2));
                    if (values[row][i] > 100) throw invalid("NOAA geomagnetic probability is outside 0-100", null);
                }
                found[row] = true;
            }
            for (boolean rowFound : found) if (!rowFound) throw invalid("NOAA geomagnetic forecast is missing a probability category", null);
            List<GeomagneticStormDay> days = new ArrayList<>();
            for (int i = 0; i < count; i++) days.add(new GeomagneticStormDay(start.plusDays(i), values[0][i], values[1][i], values[2][i], values[3][i]));
            return new GeomagneticStormForecastResponse(clock.instant(), issuedAt, SOURCE_URL, List.copyOf(days));
        } catch (DateTimeException | NumberFormatException error) {
            throw invalid("NOAA geomagnetic forecast contains an invalid date or probability", error);
        }
    }

    private ProviderUnavailableException invalid(String message, Throwable cause) {
        return cause == null ? new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, message)
                : new ProviderUnavailableException(ProviderFailure.INVALID_RESPONSE, message, cause);
    }
}
