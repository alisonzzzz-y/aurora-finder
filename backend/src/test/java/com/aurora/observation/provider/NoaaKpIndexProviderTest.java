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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class NoaaKpIndexProviderTest {
    private final AtomicReference<Reply> reply = new AtomicReference<>();
    private HttpServer server;
    private NoaaKpIndexProvider provider;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/kp", this::respond);
        server.start();
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T15:00:00Z"), ZoneOffset.UTC);
        provider = new NoaaKpIndexProvider(HttpClient.newHttpClient(), new ObjectMapper(), clock,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/kp", Duration.ofSeconds(2));
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void preservesRecordTypesAndInterpretsNoaaTimeTagsAsUtc() {
        reply.set(new Reply(200, """
                [
                  {"time_tag":"2026-09-24T12:00:00","kp":2.33,"observed":"estimated","noaa_scale":null},
                  {"time_tag":"2026-09-24T15:00:00","kp":3.67,"observed":"predicted","noaa_scale":"G1"},
                  {"time_tag":"2026-09-24T09:00:00","kp":4.33,"observed":"observed","noaa_scale":null}
                ]
                """));

        var result = provider.latest();

        assertEquals(Instant.parse("2026-09-24T15:00:00Z"), result.retrievedAt());
        assertEquals("2026-09-24T12:00:00Z", result.records().getFirst().periodStart().toString());
        assertEquals("ESTIMATED", result.records().getFirst().type().name());
        assertEquals(2.33, result.records().getFirst().kp());
        assertEquals("G1", result.records().get(1).noaaScale());
        assertNull(result.records().get(1).geomagneticStormScale(),
                "provider parsing leaves Kp threshold classification to the service layer");
        assertEquals("OBSERVED", result.records().getLast().type().name());
    }

    @Test
    void rejectsEmptyMalformedAndOutOfRangeDataAndSeparatesRateLimiting() {
        reply.set(new Reply(200, "[]"));
        assertEquals(ProviderFailure.INVALID_RESPONSE,
                assertThrows(ProviderUnavailableException.class, () -> provider.latest()).failure());

        reply.set(new Reply(200, "[{\"time_tag\":\"2026-09-24T12:00:00\",\"kp\":10,\"observed\":\"predicted\"}]"));
        assertEquals(ProviderFailure.INVALID_RESPONSE,
                assertThrows(ProviderUnavailableException.class, () -> provider.latest()).failure());

        reply.set(new Reply(200, "not json"));
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
