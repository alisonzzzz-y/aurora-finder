package com.aurora.observation.service;

import com.aurora.observation.dto.KpIndexRecord;
import com.aurora.observation.dto.KpIndexResponse;
import com.aurora.observation.dto.KpIndexType;
import com.aurora.observation.provider.KpIndexProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KpIndexServiceTest {
    @Test
    void classifiesNoaaActivityBandsAndCachesTheSharedForecast() {
        AtomicInteger calls = new AtomicInteger();
        KpIndexProvider provider = () -> {
            calls.incrementAndGet();
            return new KpIndexResponse(Instant.parse("2026-09-24T12:00:00Z"), "NOAA", List.of(
                    new KpIndexRecord(Instant.parse("2026-09-24T12:00:00Z"), 2.67,
                            KpIndexType.PREDICTED, null, null),
                    new KpIndexRecord(Instant.parse("2026-09-24T15:00:00Z"), 3.00,
                            KpIndexType.PREDICTED, null, null),
                    new KpIndexRecord(Instant.parse("2026-09-24T18:00:00Z"), 5.67,
                            KpIndexType.PREDICTED, null, null),
                    new KpIndexRecord(Instant.parse("2026-09-24T21:00:00Z"), 6.00,
                            KpIndexType.PREDICTED, "G1", null)));
        };
        KpIndexService service = new KpIndexService(provider,
                Clock.fixed(Instant.parse("2026-09-24T11:00:00Z"), ZoneOffset.UTC));

        var first = service.latest();
        var cached = service.latest();

        assertEquals("LOW", first.records().get(0).activityLevel().name());
        assertEquals("MEDIUM", first.records().get(1).activityLevel().name());
        assertEquals("MEDIUM", first.records().get(2).activityLevel().name());
        assertEquals("HIGH", first.records().get(3).activityLevel().name());
        assertEquals(1, calls.get());
        assertEquals(first, cached);
    }
}
