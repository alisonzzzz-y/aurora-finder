package com.aurora.observation.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssistantRequestLimiterTest {
    @Test
    void limitsRepeatedRequestsFromOneClient() {
        AssistantRequestLimiter limiter = new AssistantRequestLimiter(
                1, Duration.ofMinutes(10), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        assertDoesNotThrow(() -> limiter.check("client-a"));
        assertThrows(AssistantRateLimitException.class, () -> limiter.check("client-a"));
        assertDoesNotThrow(() -> limiter.check("client-b"));
    }
}
