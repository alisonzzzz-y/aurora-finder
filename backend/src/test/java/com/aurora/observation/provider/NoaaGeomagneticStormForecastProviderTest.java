package com.aurora.observation.provider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoaaGeomagneticStormForecastProviderTest {
    private static final String VALID = """
            :Product: Geomagnetic Forecast
            :Issued: 2026 Sep 24 2205 UTC
            NOAA Geomagnetic Activity Probabilities 25 Sep-27 Sep
            Active                40/40/25
            Minor storm           25/10/05
            Moderate storm        05/01/01
            Strong-Extreme storm  01/01/01
            """;
    private final AtomicReference<Reply> reply = new AtomicReference<>();
    private HttpServer server;
    private NoaaGeomagneticStormForecastProvider provider;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forecast", this::respond);
        server.start();
        provider = new NoaaGeomagneticStormForecastProvider(HttpClient.newHttpClient(),
                Clock.fixed(Instant.parse("2026-09-24T22:10:00Z"), ZoneOffset.UTC),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/forecast", Duration.ofSeconds(2));
    }

    @AfterEach
    void stopServer() { if (server != null) server.stop(0); }

    @Test
    void parsesThreeDatesAndStormCategoryProbabilities() {
        reply.set(new Reply(200, VALID));
        var result = provider.latest();
        assertEquals(Instant.parse("2026-09-24T22:05:00Z"), result.issuedAt());
        assertEquals(3, result.days().size());
        assertEquals("2026-09-25", result.days().getFirst().date().toString());
        assertEquals(25, result.days().getFirst().minorStormChancePercent());
        assertEquals(1, result.days().getLast().strongExtremeStormChancePercent());
    }

    @Test
    void rejectsMissingRowsInvalidPercentagesAndRateLimit() {
        reply.set(new Reply(200, VALID.replace("Moderate storm        05/01/01\n", "")));
        assertEquals(ProviderFailure.INVALID_RESPONSE,
                assertThrows(ProviderUnavailableException.class, () -> provider.latest()).failure());
        reply.set(new Reply(200, VALID.replace("25/10/05", "125/10/05")));
        assertEquals(ProviderFailure.INVALID_RESPONSE,
                assertThrows(ProviderUnavailableException.class, () -> provider.latest()).failure());
        reply.set(new Reply(429, ""));
        assertEquals(ProviderFailure.RATE_LIMITED,
                assertThrows(ProviderUnavailableException.class, () -> provider.latest()).failure());
    }

    private void respond(HttpExchange exchange) throws IOException {
        Reply current = reply.get();
        byte[] body = current.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(current.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) exchange.getResponseBody().write(body);
        exchange.close();
    }

    private record Reply(int status, String body) {}
}
