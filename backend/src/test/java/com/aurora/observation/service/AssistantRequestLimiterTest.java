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

    @Test
    void globalBudgetAppliesAcrossClientsAndResetsAfterWindow() {
        AdjustableClock clock = new AdjustableClock();
        var limiter = new AssistantRequestLimiter(8, Duration.ofMinutes(10), clock, 2, 1);
        limiter.check("a"); limiter.acquire().close();
        limiter.check("b"); limiter.acquire().close();
        limiter.check("c");
        assertThrows(AssistantRateLimitException.class, limiter::acquire);
        clock.advance(Duration.ofMinutes(10));
        assertDoesNotThrow(() -> limiter.acquire().close());
    }

    @Test
    void concurrencyPermitReleasesOnceAndAfterFailure() {
        var limiter = new AssistantRequestLimiter(8, Duration.ofMinutes(10), new AdjustableClock(), 24, 1);
        var first = limiter.acquire();
        assertThrows(AssistantRateLimitException.class, limiter::acquire);
        first.close(); first.close();
        var second = limiter.acquire();
        assertThrows(AssistantRateLimitException.class, limiter::acquire);
        second.close();
        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = limiter.acquire()) { throw new IllegalStateException("provider failure"); }
        });
        assertDoesNotThrow(() -> limiter.acquire().close());
    }

    @Test
    void sharedLimitRejectionDoesNotUseTheClientsAllowance() {
        var limiter = new AssistantRequestLimiter(1, Duration.ofMinutes(10), new AdjustableClock(), 24, 1);
        var busy = limiter.admit("a");
        assertThrows(AssistantRateLimitException.class, () -> limiter.admit("b"));
        busy.close();
        assertDoesNotThrow(() -> limiter.admit("b").close());
        assertThrows(AssistantRateLimitException.class, () -> limiter.admit("b"));
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
