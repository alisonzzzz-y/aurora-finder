package com.aurora.observation.service;

import com.aurora.observation.dto.KpIndexResponse;
import com.aurora.observation.dto.KpIndexRecord;
import com.aurora.observation.dto.AuroraActivityLevel;
import com.aurora.observation.dto.NoaaGeomagneticStormScale;
import com.aurora.observation.provider.KpIndexProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class KpIndexService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final KpIndexProvider provider;
    private final Clock clock;
    private CacheEntry cache;

    public KpIndexService(KpIndexProvider provider, Clock clock) {
        this.provider = provider;
        this.clock = clock;
    }

    public synchronized KpIndexResponse latest() {
        Instant now = clock.instant();
        if (cache != null && now.isBefore(cache.expiresAt())) return cache.data();

        KpIndexResponse upstream = provider.latest();
        List<KpIndexRecord> records = upstream.records().stream()
                .map(record -> new KpIndexRecord(record.periodStart(), record.kp(), record.type(),
                        record.noaaScale(), activityLevel(record.kp()),
                        NoaaGeomagneticStormScale.fromKp(record.kp())))
                .toList();
        KpIndexResponse response = new KpIndexResponse(upstream.retrievedAt(), upstream.source(), records);
        cache = new CacheEntry(response, now.plus(CACHE_TTL));
        return response;
    }

    private AuroraActivityLevel activityLevel(double kp) {
        if (kp < 3) return AuroraActivityLevel.LOW;
        if (kp < 6) return AuroraActivityLevel.MEDIUM;
        return AuroraActivityLevel.HIGH;
    }

    private record CacheEntry(KpIndexResponse data, Instant expiresAt) {}
}
