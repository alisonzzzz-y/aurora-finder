package com.aurora.observation.service;

import com.aurora.observation.dto.GeomagneticWarningsResponse;
import com.aurora.observation.provider.NoaaGeomagneticWarningsProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

@Service
public class GeomagneticWarningsService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(2);
    private final NoaaGeomagneticWarningsProvider provider;
    private final SingleFlightCache<GeomagneticWarningsResponse> cache;

    public GeomagneticWarningsService(NoaaGeomagneticWarningsProvider provider, Clock clock) {
        this.provider = provider;
        this.cache = new SingleFlightCache<>(clock, CACHE_TTL);
    }

    public GeomagneticWarningsResponse latest() {
        return cache.get(provider::latest).value();
    }
}
