package com.aurora.observation.service;

import com.aurora.observation.dto.GeomagneticWarningsResponse;
import com.aurora.observation.provider.NoaaGeomagneticWarningsProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class GeomagneticWarningsService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(2);
    private final NoaaGeomagneticWarningsProvider provider;
    private final Clock clock;
    private CacheEntry cache;

    public GeomagneticWarningsService(NoaaGeomagneticWarningsProvider provider, Clock clock) {
        this.provider = provider;
        this.clock = clock;
    }

    public synchronized GeomagneticWarningsResponse latest() {
        Instant now = clock.instant();
        if (cache != null && now.isBefore(cache.expiresAt())) return cache.response();
        GeomagneticWarningsResponse response = provider.latest();
        cache = new CacheEntry(response, now.plus(CACHE_TTL));
        return response;
    }

    private record CacheEntry(GeomagneticWarningsResponse response, Instant expiresAt) {}
}
