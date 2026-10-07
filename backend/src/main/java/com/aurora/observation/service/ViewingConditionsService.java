package com.aurora.observation.service;

import com.aurora.observation.dto.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ViewingConditionsService {
    private final SolarDarknessService solar;
    public ViewingConditionsService(SolarDarknessService solar) { this.solar = solar; }

    public ViewingConditions evaluate(Location location, SourceFact<LocalAuroraActivityResponse> aurora,
                                      SourceFact<WeatherForecastResponse> clouds, Instant now) {
        var activity = aurora.data();
        if (aurora.status() != FactFetchStatus.CURRENT || activity == null
                || activity.status() != ForecastStatus.CURRENT || activity.modelValue() == null
                || activity.modelValue() < 0 || activity.modelValue() > 100
                || activity.observationTime() == null || activity.forecastTime() == null
                || !activity.forecastTime().isAfter(now)) return missing("AURORA_UNAVAILABLE");
        if (clouds.status() != FactFetchStatus.CURRENT || clouds.data() == null
                || clouds.data().expiresAt() == null || !clouds.data().expiresAt().isAfter(now)) return missing("CLOUD_UNAVAILABLE");
        var point = clouds.data().cloudForecast().stream()
                .filter(p -> p.cloudCoverPercent() != null && Double.isFinite(p.cloudCoverPercent())
                        && p.cloudCoverPercent() >= 0 && p.cloudCoverPercent() <= 100)
                .filter(p -> !p.validAt().isBefore(now) && !p.validAt().isBefore(activity.observationTime())
                        && p.validAt().isBefore(activity.forecastTime()))
                .min(Comparator.comparing(WeatherCloudPoint::validAt)).orElse(null);
        if (point == null) return missing("NO_SHARED_TIME");
        var darkness = solar.forWindow(location, point.validAt(), point.validAt().plusSeconds(1)).thresholds().stream()
                .filter(t -> t.threshold() == SolarDarkness.Threshold.NAUTICAL_TWILIGHT).findFirst().orElse(null);
        if (darkness == null || darkness.status() == SolarDarkness.Status.CALCULATION_FAILED)
            return missing("DARKNESS_UNAVAILABLE");
        boolean dark = darkness.intervals().stream().anyMatch(i -> !point.validAt().isBefore(i.startUtc()) && point.validAt().isBefore(i.endUtc()));
        int signal = activity.modelValue();
        double cloud = point.cloudCoverPercent();
        var reasons = new ArrayList<String>();
        if (!dark) reasons.add("NOT_DARK");
        if (cloud >= 70) reasons.add("CLOUDY");
        if (signal < 18) reasons.add("WEAK_SIGNAL");
        OutlookLevel level = !reasons.isEmpty() ? OutlookLevel.LOW
                : signal >= 50 && cloud <= 30 ? OutlookLevel.HIGH : OutlookLevel.MEDIUM;
        if (reasons.isEmpty()) reasons.add(level == OutlookLevel.HIGH ? "FAVOURABLE_FACTORS" : "MIXED_FACTORS");
        Instant until = activity.forecastTime();
        return new ViewingConditions(level, point.validAt(), until, signal, cloud, dark,
                List.copyOf(reasons), "short-range-conditions-v1", false);
    }
    private ViewingConditions missing(String reason) {
        return new ViewingConditions(OutlookLevel.INSUFFICIENT_DATA, null, null, null, null, null,
                List.of(reason), "short-range-conditions-v1", false);
    }
}
