package com.aurora.observation.service;

import com.aurora.observation.dto.GeomagneticStormForecastResponse;
import com.aurora.observation.provider.GeomagneticStormForecastProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

@Service
public class GeomagneticStormForecastService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private final GeomagneticStormForecastProvider provider;
    private final SingleFlightCache<GeomagneticStormForecastResponse> cache;

    public GeomagneticStormForecastService(GeomagneticStormForecastProvider provider, Clock clock) {
        this.provider = provider;
        this.cache = new SingleFlightCache<>(clock, CACHE_TTL);
    }

    public GeomagneticStormForecastResponse latest() {
        return cache.get(provider::latest).value();
    }
}
