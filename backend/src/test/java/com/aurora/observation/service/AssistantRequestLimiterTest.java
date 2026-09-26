package com.aurora.observation.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void boundsTrackedClientsAndRemovesExpiredEntriesBeforeRejectingNewClients() {
        AdjustableClock clock = new AdjustableClock();
        AssistantRequestLimiter limiter = new AssistantRequestLimiter(
                8, Duration.ofMinutes(10), clock);

        for (int i = 0; i < 10_000; i++) {
            limiter.check("client-" + i);
        }
        assertEquals(10_000, limiter.trackedClientCount());
        assertThrows(AssistantRateLimitException.class, () -> limiter.check("client-over-cap"));

        clock.advance(Duration.ofMinutes(11));
        assertDoesNotThrow(() -> limiter.check("client-after-expiry"));
        assertEquals(1, limiter.trackedClientCount());
    }

    private static final class AdjustableClock extends Clock {
        private Instant instant = Instant.EPOCH;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
