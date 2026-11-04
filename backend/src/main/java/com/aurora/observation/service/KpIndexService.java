package com.aurora.observation.service;

import com.aurora.observation.dto.KpIndexResponse;
import com.aurora.observation.dto.KpIndexRecord;
import com.aurora.observation.dto.AuroraActivityLevel;
import com.aurora.observation.dto.NoaaGeomagneticStormScale;
import com.aurora.observation.provider.KpIndexProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
public class KpIndexService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final KpIndexProvider provider;
    private final SingleFlightCache<KpIndexResponse> cache;

    public KpIndexService(KpIndexProvider provider, Clock clock) {
        this.provider = provider;
        this.cache = new SingleFlightCache<>(clock, CACHE_TTL);
    }

    public KpIndexResponse latest() {
        return cache.get(this::load).value();
    }

    private KpIndexResponse load() {
        KpIndexResponse upstream = provider.latest();
        List<KpIndexRecord> records = upstream.records().stream()
                .map(record -> new KpIndexRecord(record.periodStart(), record.kp(), record.type(),
                        record.noaaScale(), activityLevel(record.kp()),
                        NoaaGeomagneticStormScale.fromKp(record.kp())))
                .toList();
        return new KpIndexResponse(upstream.retrievedAt(), upstream.source(), records);
    }

    private AuroraActivityLevel activityLevel(double kp) {
        if (kp < 3) return AuroraActivityLevel.LOW;
        if (kp < 6) return AuroraActivityLevel.MEDIUM;
        return AuroraActivityLevel.HIGH;
    }
}
