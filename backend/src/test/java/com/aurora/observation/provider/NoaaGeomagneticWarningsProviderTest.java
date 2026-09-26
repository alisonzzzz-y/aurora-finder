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
    void parsesTheLatestDailyStormWatchAndDropsPastUtcDates() {
        reply.set(new Reply(200, """
                [
                  {"product_id":"A20F","issue_datetime":"2026-09-25 19:00:00.000","message":"WATCH: Geomagnetic Storm Category G2 Predicted\\nHighest Storm Level Predicted by Day:\\nSep 24: G1 (Minor) Sep 25: None (Below G1) Sep 26: G2 (Moderate) Sep 27: G1 (Minor)"},
                  {"product_id":"A20F","issue_datetime":"2026-09-24 19:00:00.000","message":"WATCH: older\\nSep 25: G1"}
                ]
                """));
        var days = provider.latest().stormWatchDays();
        assertEquals(3, days.size());
        assertEquals("2026-09-25", days.get(0).date().toString());
        assertNull(days.get(0).noaaScale());
        assertEquals("G2", days.get(1).noaaScale());
    }

    @Test
    void latestCancellationClearsAnEarlierStormWatch() {
        reply.set(new Reply(200, """
                [
                  {"product_id":"A20F","issue_datetime":"2026-09-25 20:00:00.000","message":"CANCEL WATCH: Geomagnetic Storm Category G1 Predicted"},
                  {"product_id":"A20F","issue_datetime":"2026-09-25 19:00:00.000","message":"WATCH: Geomagnetic Storm Category G1 Predicted\\nSep 26: G1 (Minor)"}
                ]
                """));
        assertEquals(0, provider.latest().stormWatchDays().size());
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
