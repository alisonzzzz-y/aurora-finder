package com.aurora.observation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Caches one upstream value for a fixed time. Only one caller loads an expired value; others wait for that
 * load without holding a lock, so cached reads never queue behind a slow upstream request. Failures are not cached.
 */
public final class SingleFlightCache<T> {
    public record Loaded<T>(T value, Instant loadedAt) {}

    private final Clock clock;
    private final Duration ttl;
    private Loaded<T> current;
    private CompletableFuture<Loaded<T>> inFlight;

    public SingleFlightCache(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public Loaded<T> get(Supplier<T> loader) {
        CompletableFuture<Loaded<T>> pending;
        boolean owner = false;
        synchronized (this) {
            if (current != null && clock.instant().isBefore(current.loadedAt().plus(ttl))) return current;
            if (inFlight == null) {
                inFlight = new CompletableFuture<>();
                owner = true;
            }
            pending = inFlight;
        }
        if (!owner) return await(pending);
        try {
            Loaded<T> loaded = new Loaded<>(loader.get(), clock.instant());
            synchronized (this) {
                current = loaded;
                inFlight = null;
            }
            pending.complete(loaded);
            return loaded;
        } catch (RuntimeException | Error failure) {
            synchronized (this) { inFlight = null; }
            pending.completeExceptionally(failure);
            throw failure;
        }
    }

    private Loaded<T> await(CompletableFuture<Loaded<T>> pending) {
        try {
            return pending.join();
        } catch (CompletionException error) {
            if (error.getCause() instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (error.getCause() instanceof Error fatalFailure) throw fatalFailure;
            throw error;
        }
    }
}
