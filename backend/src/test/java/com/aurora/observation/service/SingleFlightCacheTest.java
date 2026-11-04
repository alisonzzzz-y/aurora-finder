package com.aurora.observation.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SingleFlightCacheTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void concurrentCallersShareOneSlowLoad() throws Exception {
        var cache = new SingleFlightCache<String>(clock, Duration.ofMinutes(5));
        var loads = new AtomicInteger();
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(4);
        try {
            var futures = java.util.stream.IntStream.range(0, 4).mapToObj(i -> pool.submit(() -> cache.get(() -> {
                loads.incrementAndGet();
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return "value";
            }).value())).toList();
            Thread.sleep(100);
            release.countDown();
            for (var future : futures) assertEquals("value", future.get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, loads.get());
    }

    @Test
    void doesNotCacheFailures() {
        var cache = new SingleFlightCache<String>(clock, Duration.ofMinutes(5));
        assertThrows(IllegalStateException.class, () -> cache.get(() -> { throw new IllegalStateException("down"); }));
        assertEquals("ok", cache.get(() -> "ok").value());
    }
}
