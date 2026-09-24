package com.aurora.observation.service;

import com.aurora.observation.dto.AuroraMapResponse;
import com.aurora.observation.dto.AuroraMapPoint;
import com.aurora.observation.dto.LocalAuroraActivityLevel;
import com.aurora.observation.dto.LocalAuroraActivityResponse;
import com.aurora.observation.dto.OvationForecast;
import com.aurora.observation.dto.OvationGridPoint;
import com.aurora.observation.dto.ForecastStatus;
import com.aurora.observation.provider.OvationProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

@Service
public class AuroraMapService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final OvationProvider provider;
    private final Clock clock;
    private CacheEntry<OvationForecast> cache;

    public AuroraMapService(OvationProvider provider, Clock clock) {
        this.provider = provider;
        this.clock = clock;
    }

    public synchronized AuroraMapResponse latest() {
        CacheEntry<OvationForecast> entry = latestForecast();
        OvationForecast latest = entry.data();
        ForecastStatus status = isExpired(latest, clock.instant()) ? ForecastStatus.EXPIRED : ForecastStatus.CURRENT;
        return new AuroraMapResponse(status, latest.observationTime(), latest.forecastTime(), entry.retrievedAt(), latest.source(),
                status == ForecastStatus.EXPIRED ? java.util.List.of()
                        : latest.points().stream().filter(point -> point.auroraValue() > 0 && Math.abs(point.latitude()) < 90)
                        .map(point -> new AuroraMapPoint(point.longitude(), point.latitude(), point.auroraValue()))
                        .toList());
    }

    public synchronized LocalAuroraActivityResponse forCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new InvalidLocationRequestException("Coordinates are outside the valid latitude/longitude range.");
        }
        CacheEntry<OvationForecast> entry = latestForecast();
        OvationForecast latest = entry.data();
        if (isExpired(latest, clock.instant())) {
            return new LocalAuroraActivityResponse(ForecastStatus.EXPIRED, LocalAuroraActivityLevel.INSUFFICIENT_DATA,
                    null, null, null, latest.observationTime(), latest.forecastTime(), entry.retrievedAt(),
                    latest.source(), "ovation-local-v1");
        }
        OvationGridPoint nearest = latest.points().stream()
                .min(Comparator.comparingDouble(point -> distanceKm(latitude, longitude,
                        point.latitude(), point.longitude())))
                .orElseThrow(() -> new IllegalStateException("NOAA grid is empty."));
        int value = nearest.auroraValue();
        LocalAuroraActivityLevel level = value < 18 ? LocalAuroraActivityLevel.LOW
                : value < 50 ? LocalAuroraActivityLevel.MEDIUM : LocalAuroraActivityLevel.HIGH;
        return new LocalAuroraActivityResponse(ForecastStatus.CURRENT, level, value, nearest.longitude(), nearest.latitude(),
                latest.observationTime(), latest.forecastTime(), entry.retrievedAt(), latest.source(), "ovation-local-v1");
    }

    private static boolean isExpired(OvationForecast forecast, Instant now) {
        return now.isAfter(forecast.forecastTime());
    }

    private CacheEntry<OvationForecast> latestForecast() {
        Instant now = clock.instant();
        if (cache != null && now.isBefore(cache.expiresAt())) return cache;
        OvationForecast latest = provider.latest();
        Instant retrievedAt = clock.instant();
        cache = new CacheEntry<>(latest, retrievedAt, retrievedAt.plus(CACHE_TTL));
        return cache;
    }

    private static double distanceKm(double latitude1, double longitude1, double latitude2, double longitude2) {
        double lat1 = Math.toRadians(latitude1);
        double lat2 = Math.toRadians(latitude2);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record CacheEntry<T>(T data, Instant retrievedAt, Instant expiresAt) {}
}
