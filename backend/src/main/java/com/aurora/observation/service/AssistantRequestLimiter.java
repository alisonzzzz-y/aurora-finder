package com.aurora.observation.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.HashMap;

@Service
public class AssistantRequestLimiter {
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private final int requestLimit;
    private final int globalLimit;
    private final int maxConcurrent;
    private int active;
    private final Deque<Instant> globalRequests = new ArrayDeque<>();
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> requests = new HashMap<>();

    @Autowired
    public AssistantRequestLimiter(@Value("${app.assistant.rate-limit.requests:8}") int requestLimit,
                                   @Value("${app.assistant.rate-limit.window:10m}") Duration window,
                                   @Value("${app.assistant.rate-limit.global-requests:24}") int globalLimit,
                                   @Value("${app.assistant.max-concurrent:2}") int maxConcurrent) {
        this(requestLimit, window, Clock.systemUTC(), globalLimit, maxConcurrent);
    }

    public AssistantRequestLimiter(int requestLimit, Duration window) { this(requestLimit, window, Clock.systemUTC()); }

    AssistantRequestLimiter(int requestLimit, Duration window, Clock clock) { this(requestLimit, window, clock, 24, 2); }

    AssistantRequestLimiter(int requestLimit, Duration window, Clock clock, int globalLimit, int maxConcurrent) {
        if (requestLimit < 1 || globalLimit < 1 || maxConcurrent < 1 || window.isNegative() || window.isZero()) throw new IllegalArgumentException("Invalid assistant limits");
        this.globalLimit = globalLimit;
        this.maxConcurrent = maxConcurrent;
        this.requestLimit = requestLimit;
        this.window = window;
        this.clock = clock;
    }

    public synchronized void check(String clientKey) {
        Instant now = clock.instant();
        if (!requests.containsKey(clientKey) && requests.size() >= MAX_TRACKED_CLIENTS) {
            Instant cutoff = now.minus(window);
            requests.entrySet().removeIf(entry -> {
                prune(entry.getValue(), cutoff);
                return entry.getValue().isEmpty();
            });
            if (requests.size() >= MAX_TRACKED_CLIENTS) throw new AssistantRateLimitException();
        }
        Deque<Instant> recent = requests.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());
        Instant cutoff = now.minus(window);
        prune(recent, cutoff);
        if (recent.size() >= requestLimit) throw new AssistantRateLimitException();
        recent.addLast(now);
    }

    public synchronized Permit acquire() {
        Instant now = clock.instant();
        prune(globalRequests, now.minus(window));
        if (active >= maxConcurrent || globalRequests.size() >= globalLimit) throw new AssistantRateLimitException();
        active++;
        globalRequests.addLast(now);
        return new Permit();
    }

    public final class Permit implements AutoCloseable {
        private boolean released;
        @Override public void close() {
            synchronized (AssistantRequestLimiter.this) {
                if (!released) { active--; released = true; }
            }
        }
    }

    synchronized int trackedClientCount() {
        return requests.size();
    }

    private void prune(Deque<Instant> recent, Instant cutoff) {
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) recent.removeFirst();
    }
}
