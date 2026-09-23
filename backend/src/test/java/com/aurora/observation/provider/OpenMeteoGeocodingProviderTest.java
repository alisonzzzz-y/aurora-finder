package com.aurora.observation.provider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenMeteoGeocodingProviderTest {
    private final AtomicReference<Reply> reply = new AtomicReference<>();
    private HttpServer server;
    private OpenMeteoGeocodingProvider provider;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", this::respond);
        server.createContext("/get", this::respond);
        server.start();
        provider = new OpenMeteoGeocodingProvider(HttpClient.newHttpClient(), new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort(), true, Duration.ofSeconds(2));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void distinguishesARealEmptySearchFromMalformedData() {
        reply.set(new Reply(200, "{\"generationtime_ms\":0.1}"));
        assertTrue(provider.search("Nowhere").isEmpty());

        reply.set(new Reply(200, "{}"));
        assertFailure(ProviderFailure.INVALID_RESPONSE);

        reply.set(new Reply(200, "{broken"));
        assertFailure(ProviderFailure.INVALID_RESPONSE);
    }

    @Test
    void rejectsInvalidCoordinatesTimeZoneAndMissingFields() {
        reply.set(new Reply(200, result("91", "Europe/Dublin")));
        assertFailure(ProviderFailure.INVALID_RESPONSE);

        reply.set(new Reply(200, result("53.33306", "Invalid/Zone")));
        assertFailure(ProviderFailure.INVALID_RESPONSE);

        reply.set(new Reply(200, "{\"results\":[{\"id\":2964574,\"name\":\"Dublin\"}]}"));
        assertFailure(ProviderFailure.INVALID_RESPONSE);
    }

    @Test
    void separatesNotFoundAndRateLimited() {
        reply.set(new Reply(404, ""));
        assertTrue(provider.get(999).isEmpty());

        reply.set(new Reply(429, ""));
        assertFailure(ProviderFailure.RATE_LIMITED);

        reply.set(new Reply(504, ""));
        assertFailure(ProviderFailure.TIMEOUT);
    }

    @Test
    void parsesValidLocation() {
        reply.set(new Reply(200, result("53.33306", "Europe/Dublin")));
        assertEquals("Europe/Dublin", provider.search("Dublin").getFirst().timezone());
        reply.set(new Reply(200, location("53.33306", "Europe/Dublin")));
        assertEquals(2964574, provider.get(2964574).orElseThrow().id());
        assertEquals(ProviderFailure.INVALID_RESPONSE, assertThrows(ProviderUnavailableException.class,
                () -> provider.get(12)).failure());
    }

    private void assertFailure(ProviderFailure expected) {
        assertEquals(expected, assertThrows(ProviderUnavailableException.class,
                () -> provider.search("Dublin")).failure());
    }

    private String result(String latitude, String timezone) {
        return "{\"results\":[" + location(latitude, timezone) + "]}";
    }

    private String location(String latitude, String timezone) {
        return "{\"id\":2964574,\"name\":\"Dublin\",\"latitude\":" + latitude
                + ",\"longitude\":-6.24889,\"timezone\":\"" + timezone + "\"}";
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
