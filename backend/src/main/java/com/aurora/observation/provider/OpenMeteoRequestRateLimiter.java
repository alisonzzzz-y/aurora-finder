package com.aurora.observation.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

@Component
public class OpenMeteoRequestRateLimiter {
    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final Clock clock;
    private final int perMinute;
    private final int perHour;
    private final int perDay;
    private final Deque<Instant> requests = new ArrayDeque<>();
    private final Deque<Instant> lookups = new ArrayDeque<>();

    public OpenMeteoRequestRateLimiter(Clock clock,
                                       @Value("${app.geocoding.rate-limit.per-minute:500}") int perMinute,
                                       @Value("${app.geocoding.rate-limit.per-hour:4500}") int perHour,
                                       @Value("${app.geocoding.rate-limit.per-day:9000}") int perDay) {
        if (perMinute < 1 || perHour < 1 || perDay < 1
                || perMinute > perHour || perHour > perDay) {
            throw new IllegalArgumentException("Open-Meteo request limits must be positive and ordered by time window.");
        }
        this.clock = clock;
        this.perMinute = perMinute;
        this.perHour = perHour;
        this.perDay = perDay;
    }

    public synchronized void acquire() {
        acquire(false);
    }

    /**
     * Location-ID lookups come from public URLs that accept any ID, so they may use at most half of each
     * window. Place searches always keep the remaining budget.
     */
    public synchronized void acquire(boolean idLookup) {
        Instant now = clock.instant();
        Instant dayCutoff = now.minus(DAY);
        while (!requests.isEmpty() && !requests.peekFirst().isAfter(dayCutoff)) requests.removeFirst();
        while (!lookups.isEmpty() && !lookups.peekFirst().isAfter(dayCutoff)) lookups.removeFirst();

        if (countWithin(requests, now, MINUTE) >= perMinute || countWithin(requests, now, HOUR) >= perHour
                || requests.size() >= perDay
                || idLookup && (countWithin(lookups, now, MINUTE) >= Math.max(1, perMinute / 2)
                    || countWithin(lookups, now, HOUR) >= Math.max(1, perHour / 2)
                    || lookups.size() >= Math.max(1, perDay / 2))) {
            throw new ProviderUnavailableException(ProviderFailure.RATE_LIMITED,
                    "Open-Meteo local request budget is exhausted");
        }
        requests.addLast(now);
        if (idLookup) lookups.addLast(now);
    }

    private long countWithin(Deque<Instant> entries, Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        return entries.stream().filter(requestedAt -> requestedAt.isAfter(cutoff)).count();
    }
}
