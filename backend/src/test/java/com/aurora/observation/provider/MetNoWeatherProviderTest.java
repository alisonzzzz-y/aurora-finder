package com.aurora.observation.provider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetNoWeatherProviderTest {
    private static final String BODY = """
            {"properties":{"timeseries":[
              {"time":"2026-09-24T15:00:00Z","data":{"instant":{"details":{"cloud_area_fraction":42.5}}}},
              {"time":"2026-09-24T16:00:00Z","data":{"instant":{"details":{}}}},
              {"time":"2026-09-24T22:00:00Z","data":{"instant":{"details":{"cloud_area_fraction":0}}}}
            ]}}
            """;
    private final AtomicReference<Reply> reply = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> requestTarget = new AtomicReference<>();
    private final AtomicReference<String> userAgent = new AtomicReference<>();
    private final AtomicReference<String> ifModifiedSince = new AtomicReference<>();
    private MutableClock clock;
    private HttpServer server;
    private MetNoWeatherProvider provider;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/weather", this::respond);
        server.start();
        clock = new MutableClock(Instant.parse("2026-09-24T14:55:00Z"));
        provider = new MetNoWeatherProvider(HttpClient.newHttpClient(), new ObjectMapper(), clock,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/weather", "AuroraFinder/test contact@example.com",
                Duration.ofSeconds(2), Duration.ofMinutes(15), 10);
        reply.set(new Reply(200, BODY, 600, false));
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void parsesCloudPercentAndKeepsTheSourceTimeIntervalsAndMissingValues() {
        var result = provider.forecast(53.34987, -6.26039);

        assertEquals("/weather?lat=53.3498&lon=-6.2603", requestTarget.get());
        assertEquals("AuroraFinder/test contact@example.com", userAgent.get());
        assertTrue(serverAcceptsCompression());
        assertEquals(3, result.cloudForecast().size());
        assertEquals(42.5, result.cloudForecast().getFirst().cloudCoverPercent());
        assertNull(result.cloudForecast().get(1).cloudCoverPercent());
        assertEquals(Instant.parse("2026-09-24T22:00:00Z"), result.cloudForecast().getLast().validAt());
        assertEquals(Instant.parse("2026-09-24T15:05:00Z"), result.expiresAt());
    }

    @Test
    void cachesUntilExpiresThenUsesLastModifiedAndReusesDataAfterNotModified() {
        var original = provider.forecast(65.0, 18.0);
        provider.forecast(65.0, 18.0);
        assertEquals(1, requests.get());

        clock.advance(Duration.ofMinutes(11));
        reply.set(new Reply(304, "", 600, false));
        var refreshed = provider.forecast(65.0, 18.0);

        assertEquals(2, requests.get());
        assertEquals("Thu, 24 Sep 2026 14:55:00 GMT", ifModifiedSince.get());
        assertEquals(original.retrievedAt(), refreshed.retrievedAt());
        assertEquals(Instant.parse("2026-09-24T15:16:00Z"), refreshed.expiresAt());
    }

    @Test
    void decompressesGzipAndRejectsMalformedOrOutOfRangeForecasts() {
        reply.set(new Reply(200, BODY, 600, true));
        assertEquals(3, provider.forecast(1, 1).cloudForecast().size());

        reply.set(new Reply(200, "not json", 600, false));
        assertEquals(ProviderFailure.INVALID_RESPONSE,
                assertThrows(ProviderUnavailableException.class, () -> provider.forecast(2, 2)).failure());

        reply.set(new Reply(200, BODY.replace("42.5", "100.1"), 600, false));
        assertEquals(ProviderFailure.INVALID_RESPONSE,
                assertThrows(ProviderUnavailableException.class, () -> provider.forecast(3, 3)).failure());

        reply.set(new Reply(429, "", 600, false));
        assertEquals(ProviderFailure.RATE_LIMITED,
                assertThrows(ProviderUnavailableException.class, () -> provider.forecast(4, 4)).failure());
        reply.set(new Reply(403, "", 600, false));
        assertEquals(ProviderFailure.FORBIDDEN,
                assertThrows(ProviderUnavailableException.class, () -> provider.forecast(5, 5)).failure());
        assertThrows(IllegalArgumentException.class, () -> provider.forecast(91, 0));
    }

    private boolean serverAcceptsCompression() {
        return "gzip, deflate".equals(exchangeEncoding.get());
    }

    private final AtomicReference<String> exchangeEncoding = new AtomicReference<>();

    private void respond(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        requestTarget.set(exchange.getRequestURI().toString());
        userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
        ifModifiedSince.set(exchange.getRequestHeaders().getFirst("If-Modified-Since"));
        exchangeEncoding.set(exchange.getRequestHeaders().getFirst("Accept-Encoding"));
        Reply current = reply.get();
        exchange.getResponseHeaders().add("Expires", DateTimeFormatter.RFC_1123_DATE_TIME
                .format(clock.instant().plusSeconds(current.expiresInSeconds()).atOffset(ZoneOffset.UTC)));
        if (current.status() == 200) {
            exchange.getResponseHeaders().add("Last-Modified", "Thu, 24 Sep 2026 14:55:00 GMT");
            byte[] body = current.body().getBytes(StandardCharsets.UTF_8);
            if (current.gzip()) {
                var compressed = new ByteArrayOutputStream();
                try (var gzip = new GZIPOutputStream(compressed)) { gzip.write(body); }
                body = compressed.toByteArray();
                exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            }
            exchange.sendResponseHeaders(current.status(), body.length);
            exchange.getResponseBody().write(body);
        } else {
            exchange.sendResponseHeaders(current.status(), -1);
        }
        exchange.close();
    }

    private record Reply(int status, String body, long expiresInSeconds, boolean gzip) {}

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
