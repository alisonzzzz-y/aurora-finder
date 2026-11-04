package com.aurora.observation.provider;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenMeteoRequestRateLimiterTest {
    @Test
    void idLookupsCannotUseTheBudgetThatSearchesNeed() {
        var limiter = new OpenMeteoRequestRateLimiter(
                Clock.fixed(Instant.parse("2026-10-31T00:00:00Z"), ZoneOffset.UTC), 4, 100, 1000);

        limiter.acquire(true);
        limiter.acquire(true);
        assertEquals(ProviderFailure.RATE_LIMITED,
                assertThrows(ProviderUnavailableException.class, () -> limiter.acquire(true)).failure());
        assertDoesNotThrow(() -> limiter.acquire(false));
        assertDoesNotThrow(() -> limiter.acquire(false));
        assertThrows(ProviderUnavailableException.class, () -> limiter.acquire(false));
    }
}
