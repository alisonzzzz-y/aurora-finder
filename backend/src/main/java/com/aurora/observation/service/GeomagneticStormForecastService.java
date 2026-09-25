package com.aurora.observation.service;

import com.aurora.observation.dto.GeomagneticStormForecastResponse;
import com.aurora.observation.provider.GeomagneticStormForecastProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class GeomagneticStormForecastService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private final GeomagneticStormForecastProvider provider;
    private final Clock clock;
    private CacheEntry cache;

    public GeomagneticStormForecastService(GeomagneticStormForecastProvider provider, Clock clock) {
        this.provider = provider;
        this.clock = clock;
    }

    public synchronized GeomagneticStormForecastResponse latest() {
        Instant now = clock.instant();
        if (cache != null && now.isBefore(cache.expiresAt())) return cache.data();
        GeomagneticStormForecastResponse response = provider.latest();
        cache = new CacheEntry(response, now.plus(CACHE_TTL));
        return response;
    }

    private record CacheEntry(GeomagneticStormForecastResponse data, Instant expiresAt) {}
}
