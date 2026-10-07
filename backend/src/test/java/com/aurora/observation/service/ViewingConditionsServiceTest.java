package com.aurora.observation.service;

import com.aurora.observation.dto.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ViewingConditionsServiceTest {
    private final ViewingConditionsService service = new ViewingConditionsService(new SolarDarknessService());
    private final Location dublin = new Location(2964574, "Dublin", "Leinster", "Dublin City", "Ireland", 53.33306, -6.24889, "Europe/Dublin");
    private final Instant night = Instant.parse("2026-10-07T00:00:00Z");

    @Test void gradesConditionsWithoutClaimingCalibratedProbability() {
        var high = evaluate(night, 50, 30.0, night.plusSeconds(600), FactFetchStatus.CURRENT);
        assertEquals(OutlookLevel.HIGH, high.level());
        assertFalse(high.probabilityCalibrated());
        assertEquals(night.plusSeconds(600), high.evaluatedAtUtc());
        assertEquals(OutlookLevel.MEDIUM, evaluate(night, 49, 30.0, night.plusSeconds(600), FactFetchStatus.CURRENT).level());
        assertEquals(OutlookLevel.MEDIUM, evaluate(night, 50, 31.0, night.plusSeconds(600), FactFetchStatus.CURRENT).level());
        assertEquals(OutlookLevel.MEDIUM, evaluate(night, 18, 69.0, night.plusSeconds(600), FactFetchStatus.CURRENT).level());
        assertEquals(OutlookLevel.LOW, evaluate(night, 17, 0.0, night.plusSeconds(600), FactFetchStatus.CURRENT).level());
        assertEquals(OutlookLevel.LOW, evaluate(night, 100, 70.0, night.plusSeconds(600), FactFetchStatus.CURRENT).level());
    }
    @Test void daylightIsLowEvenWithStrongAuroraAndClearSky() {
        Instant noon = night.plusSeconds(12 * 3600);
        var result = evaluate(noon, 100, 0.0, noon.plusSeconds(600), FactFetchStatus.CURRENT);
        assertEquals(OutlookLevel.LOW, result.level());
        assertTrue(result.reasons().contains("NOT_DARK"));
    }
    @Test void missingCloudsAreNotClearSkies() {
        for (Double cloud : new Double[] {null, Double.NaN, -1.0, 101.0})
            assertEquals(OutlookLevel.INSUFFICIENT_DATA, evaluate(night, 100, cloud, night.plusSeconds(600), FactFetchStatus.CURRENT).level());
    }
    @Test void doesNotExtrapolateAuroraToLaterNightsOrUsePastClouds() {
        for (Instant sample : List.of(night.minusSeconds(1), night.plusSeconds(3600), night.plusSeconds(86400)))
            assertEquals(OutlookLevel.INSUFFICIENT_DATA, evaluate(night, 100, 0.0, sample, FactFetchStatus.CURRENT).level());
    }
    @Test void unavailableSourceDoesNotProduceGrade() {
        assertEquals(OutlookLevel.INSUFFICIENT_DATA, evaluate(night, 100, 0.0, night.plusSeconds(600), FactFetchStatus.EXPIRED).level());
    }
    private ViewingConditions evaluate(Instant now, int signal, Double cloud, Instant sample, FactFetchStatus status) {
        var activity = new LocalAuroraActivityResponse(ForecastStatus.CURRENT, LocalAuroraActivityLevel.HIGH, signal,
                -6.0, 53.0, now.minusSeconds(600), now.plusSeconds(3600), now, "NOAA", "test");
        var weather = new WeatherForecastResponse(now, now.plusSeconds(7200), "MET Norway", 53.333, -6.248,
                List.of(new WeatherCloudPoint(sample, cloud)));
        return service.evaluate(dublin, fact(activity, status), fact(weather, FactFetchStatus.CURRENT), now);
    }
    private <T> SourceFact<T> fact(T value, FactFetchStatus status) {
        return new SourceFact<>(status, FactTimeScope.SHORT_RANGE, night, null, null, null, null, "test", "https://example.com", null, value);
    }
}
