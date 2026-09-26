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
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> requests = new HashMap<>();

    @Autowired
    public AssistantRequestLimiter(@Value("${app.assistant.rate-limit.requests:8}") int requestLimit,
                                   @Value("${app.assistant.rate-limit.window:10m}") Duration window) {
        this(requestLimit, window, Clock.systemUTC());
    }

    AssistantRequestLimiter(int requestLimit, Duration window, Clock clock) {
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

    synchronized int trackedClientCount() {
        return requests.size();
    }

    private void prune(Deque<Instant> recent, Instant cutoff) {
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) recent.removeFirst();
    }
}
