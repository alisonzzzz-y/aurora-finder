package com.aurora.observation.service;

import com.aurora.observation.dto.OvationForecast;
import com.aurora.observation.dto.OvationGridPoint;
import com.aurora.observation.provider.OvationProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuroraMapServiceTest {
    @Test
    void categorizesTheNearestLocalGridCellAndKeepsTheRawModelValue() {
        OvationProvider provider = () -> new OvationForecast(
                Instant.parse("2026-09-24T12:00:00Z"), Instant.parse("2026-09-24T13:00:00Z"),
                "https://example.test/ovation", List.of(
                new OvationGridPoint(-22, 65, 17),
                new OvationGridPoint(-21, 65, 18),
                new OvationGridPoint(-20, 65, 50)));
        AuroraMapService service = new AuroraMapService(provider,
                Clock.fixed(Instant.parse("2026-09-24T12:05:00Z"), ZoneOffset.UTC));

        var low = service.forCoordinates(65, -22);
        var medium = service.forCoordinates(65, -21);
        var high = service.forCoordinates(65, -20);

        assertEquals("LOW", low.level().name());
        assertEquals(17, low.modelValue());
        assertEquals(Instant.parse("2026-09-24T12:05:00Z"), low.retrievedAt());
        assertEquals("MEDIUM", medium.level().name());
        assertEquals("HIGH", high.level().name());
        assertEquals("ovation-local-v1", high.ruleVersion());
    }

    @Test
    void sharesTheRetrievedAtTimestampAcrossMapAndLocationResponsesWhileCached() {
        AtomicInteger calls = new AtomicInteger();
        OvationProvider provider = () -> {
            calls.incrementAndGet();
            return new OvationForecast(Instant.parse("2026-09-24T12:00:00Z"),
                    Instant.parse("2026-09-24T13:00:00Z"), "https://example.test/ovation",
                    List.of(new OvationGridPoint(0, 60, 20)));
        };
        AuroraMapService service = new AuroraMapService(provider,
                Clock.fixed(Instant.parse("2026-09-24T12:05:00Z"), ZoneOffset.UTC));

        var map = service.latest();
        var location = service.forCoordinates(60, 0);

        assertEquals(1, calls.get());
        assertEquals(map.retrievedAt(), location.retrievedAt());
    }

    @Test
    void expiresOvationAtForecastTimeAndSuppressesTheOldGrid() {
        OvationProvider provider = () -> new OvationForecast(
                Instant.parse("2026-09-24T11:00:00Z"), Instant.parse("2026-09-24T12:00:00Z"),
                "https://example.test/ovation", List.of(new OvationGridPoint(-22, 65, 80)));
        AuroraMapService service = new AuroraMapService(provider,
                Clock.fixed(Instant.parse("2026-09-24T12:05:00Z"), ZoneOffset.UTC));

        var map = service.latest();
        var local = service.forCoordinates(65, -22);

        assertEquals("EXPIRED", map.status().name());
        assertEquals(List.of(), map.points());
        assertEquals("EXPIRED", local.status().name());
        assertEquals("INSUFFICIENT_DATA", local.level().name());
        assertNull(local.modelValue());
    }

    @Test
    void forecastIsCurrentAtItsExactTargetTime() {
        OvationProvider provider = () -> new OvationForecast(
                Instant.parse("2026-09-24T11:00:00Z"), Instant.parse("2026-09-24T12:05:00Z"),
                "https://example.test/ovation", List.of(new OvationGridPoint(-22, 65, 80)));
        AuroraMapService service = new AuroraMapService(provider,
                Clock.fixed(Instant.parse("2026-09-24T12:05:00Z"), ZoneOffset.UTC));

        assertEquals("CURRENT", service.latest().status().name());
    }
}
