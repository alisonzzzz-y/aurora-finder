package com.aurora.observation.provider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoaaOvationProviderTest {
    private final AtomicReference<Reply> reply = new AtomicReference<>();
    private HttpServer server;
    private NoaaOvationProvider provider;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ovation", this::respond);
        server.start();
        provider = new NoaaOvationProvider(HttpClient.newHttpClient(), new ObjectMapper(),
                java.time.Clock.fixed(java.time.Instant.parse("2026-09-24T12:05:00Z"), java.time.ZoneOffset.UTC),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/ovation", Duration.ofSeconds(2));
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void parsesGridTimestampsAndWrapsLongitudesForWebMaps() {
        reply.set(new Reply(200, """
                {"Observation Time":"2026-09-23T19:59:00Z","Forecast Time":"2026-09-23T21:30:00Z",
                 "Data Format":"[Longitude, Latitude, Aurora]",
                 "coordinates":[[359,65,12],[180,-64,5],[90,90,4],[0,0,0]]}
                """));

        var result = provider.latest();

        assertEquals("2026-09-23T19:59:00Z", result.observationTime().toString());
        assertEquals("2026-09-23T21:30:00Z", result.forecastTime().toString());
        assertEquals(4, result.points().size());
        assertEquals(-1.0, result.points().getFirst().longitude());
        assertEquals(12, result.points().getFirst().auroraValue());
        assertEquals(0.0, result.points().getLast().latitude());
        assertEquals(0, result.points().getLast().auroraValue());
    }

    @Test
    void rejectsMalformedGridAndSeparatesRateLimiting() {
        reply.set(new Reply(200, "{\"coordinates\":[]}"));
        assertEquals(ProviderFailure.INVALID_RESPONSE,
                assertThrows(ProviderUnavailableException.class, () -> provider.latest()).failure());

        reply.set(new Reply(200, """
                {"Observation Time":"2026-09-23T19:59:00Z","Forecast Time":"2026-09-23T21:30:00Z",
                 "Data Format":"[Longitude, Latitude, Aurora]","coordinates":[[360,65,12]]}
                """));
        assertEquals(ProviderFailure.INVALID_RESPONSE,
                assertThrows(ProviderUnavailableException.class, () -> provider.latest()).failure());

        reply.set(new Reply(429, ""));
        assertEquals(ProviderFailure.RATE_LIMITED,
                assertThrows(ProviderUnavailableException.class, () -> provider.latest()).failure());
    }

    @Test
    void rejectsFutureObservationTimeAndForecastBeforeObservation() {
        reply.set(new Reply(200, """
                {"Observation Time":"2026-09-24T12:06:00Z","Forecast Time":"2026-09-24T13:00:00Z",
                 "Data Format":"[Longitude, Latitude, Aurora]","coordinates":[[0,65,12]]}
                """));
        assertEquals(ProviderFailure.INVALID_RESPONSE,
                assertThrows(ProviderUnavailableException.class, () -> provider.latest()).failure());

        reply.set(new Reply(200, """
                {"Observation Time":"2026-09-24T12:00:00Z","Forecast Time":"2026-09-24T11:59:00Z",
                 "Data Format":"[Longitude, Latitude, Aurora]","coordinates":[[0,65,12]]}
                """));
        assertEquals(ProviderFailure.INVALID_RESPONSE,
                assertThrows(ProviderUnavailableException.class, () -> provider.latest()).failure());
    }

    @Test
    void rejectsBlankTruncatedAndChangedGridFormatResponses() {
        List<String> invalidBodies = List.of(
                "",
                "   ",
                "{\"Observation Time\":\"2026-09-24T12:00:00Z\",",
                """
                {"Observation Time":"2026-09-24T12:00:00Z","Forecast Time":"2026-09-24T13:00:00Z",
                 "Data Format":"[Latitude, Longitude, Aurora]","coordinates":[[65,0,12]]}
                """
        );

        for (String body : invalidBodies) {
            reply.set(new Reply(200, body));
            assertEquals(ProviderFailure.INVALID_RESPONSE,
                    assertThrows(ProviderUnavailableException.class, () -> provider.latest()).failure());
        }
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
