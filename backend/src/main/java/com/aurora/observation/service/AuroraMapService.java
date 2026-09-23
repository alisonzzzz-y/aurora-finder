package com.aurora.observation.service;

import com.aurora.observation.dto.AuroraMapResponse;
import com.aurora.observation.provider.OvationProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class AuroraMapService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final OvationProvider provider;
    private final Clock clock;
    private CacheEntry cache;

    public AuroraMapService(OvationProvider provider, Clock clock) {
        this.provider = provider;
        this.clock = clock;
    }

    public synchronized AuroraMapResponse latest() {
        Instant now = clock.instant();
        if (cache != null && now.isBefore(cache.expiresAt())) return cache.data();
        AuroraMapResponse latest = provider.latest();
        cache = new CacheEntry(latest, now.plus(CACHE_TTL));
        return latest;
    }

    private record CacheEntry(AuroraMapResponse data, Instant expiresAt) {}
}
