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

class NoaaGeomagneticWarningsProviderTest {
    private final AtomicReference<Reply> reply = new AtomicReference<>();
    private HttpServer server;
    private NoaaGeomagneticWarningsProvider provider;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/alerts", this::respond);
        server.start();
        provider = new NoaaGeomagneticWarningsProvider(HttpClient.newHttpClient(), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-25T21:50:00Z"), ZoneOffset.UTC),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/alerts", Duration.ofSeconds(2));
    }

    @AfterEach
    void stopServer() { if (server != null) server.stop(0); }

    @Test
    void returnsOnlyWarningsWithAStillValidWindow() {
        reply.set(new Reply(200, """
                [
                  {"product_id":"K05W","message":"WARNING: Geomagnetic K-index of 5 expected\\nValid From: 2026 Sep 25 2154 UTC\\nValid To: 2026 Sep 26 1200 UTC\\nNoaa Scale: G1 - Minor"},
                  {"product_id":"K04W","message":"WARNING: Geomagnetic K-index of 4 expected\\nValid From: 2026 Sep 25 0800 UTC\\nValid To: 2026 Sep 25 1200 UTC"},
                  {"product_id":"EF3A","message":"Electron alert"}
                ]
                """));
        var result = provider.latest();
        assertEquals(1, result.warnings().size());
        assertEquals("K05W", result.warnings().getFirst().productId());
        assertEquals("G1 - Minor", result.warnings().getFirst().noaaScale());
        assertEquals(Instant.parse("2026-09-26T12:00:00Z"), result.warnings().getFirst().validTo());
    }

    @Test
    void rejectsInvalidJsonAndSeparatesRateLimiting() {
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
