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
        Instant now = clock.instant();
        Instant dayCutoff = now.minus(DAY);
        while (!requests.isEmpty() && !requests.peekFirst().isAfter(dayCutoff)) requests.removeFirst();

        if (countWithin(now, MINUTE) >= perMinute || countWithin(now, HOUR) >= perHour
                || requests.size() >= perDay) {
            throw new ProviderUnavailableException(ProviderFailure.RATE_LIMITED,
                    "Open-Meteo local request budget is exhausted");
        }
        requests.addLast(now);
    }

    private long countWithin(Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        return requests.stream().filter(requestedAt -> requestedAt.isAfter(cutoff)).count();
    }
}
