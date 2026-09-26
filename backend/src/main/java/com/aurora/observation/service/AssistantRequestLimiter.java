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
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AssistantRequestLimiter {
    private final int requestLimit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();

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

    public void check(String clientKey) {
        Instant now = clock.instant();
        Deque<Instant> recent = requests.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());
        synchronized (recent) {
            Instant cutoff = now.minus(window);
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) recent.removeFirst();
            if (recent.size() >= requestLimit) throw new AssistantRateLimitException();
            recent.addLast(now);
        }
        if (requests.size() > 10_000) requests.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
