package com.aurora.observation.service;

import com.aurora.observation.dto.FactFetchStatus;
import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.LocalAuroraActivityLevel;
import com.aurora.observation.dto.LocalAuroraActivityResponse;
import com.aurora.observation.dto.NightOutlook;
import com.aurora.observation.dto.ObservationFactsResponse;
import com.aurora.observation.dto.OutlookLevel;
import com.aurora.observation.dto.OutlookResponse;
import com.aurora.observation.dto.RuleStatus;
import com.aurora.observation.dto.SolarDarkness;
import com.aurora.observation.dto.SolarDarkness.Status;
import com.aurora.observation.dto.SolarDarkness.Threshold;
import com.aurora.observation.dto.SolarDarkness.ThresholdWindow;
import com.aurora.observation.dto.WeatherForecastResponse;
import com.aurora.observation.dto.WeatherCloudPoint;
import com.aurora.observation.provider.ProviderFailure;
import com.aurora.observation.provider.ProviderUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObservationFactsServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
    private static final Location DUBLIN = new Location(2964574, "Dublin", "Leinster", "County Dublin", "Ireland",
            53.33306, -6.24889, "Europe/Dublin");

    @Test
    void keepsOtherFactsAvailableWhenOneProviderFails() {
        OutlookService outlooks = mock(OutlookService.class);
        AuroraMapService aurora = mock(AuroraMapService.class);
        WeatherService weather = mock(WeatherService.class);
        when(outlooks.forLocation(DUBLIN.id())).thenReturn(outlook());
        when(aurora.forCoordinates(DUBLIN.latitude(), DUBLIN.longitude()))
                .thenThrow(new ProviderUnavailableException(ProviderFailure.TIMEOUT, "timeout"));
        when(weather.forecast(DUBLIN.latitude(), DUBLIN.longitude(), DUBLIN.timezone()))
                .thenReturn(new WeatherForecastResponse(NOW, NOW.plusSeconds(3600), "MET Norway",
                        DUBLIN.latitude(), DUBLIN.longitude(), List.of(new WeatherCloudPoint(NOW.plusSeconds(300), 45.0))));

        ObservationFactsResponse response = service(outlooks, aurora, weather).forLocation(DUBLIN.id());

        assertEquals(FactFetchStatus.PARTIAL, response.sourceStatus());
        assertEquals(FactFetchStatus.UNAVAILABLE, response.auroraActivity().status());
        assertEquals(ProviderFailure.TIMEOUT, response.auroraActivity().failureCode());
        assertNull(response.auroraActivity().data());
        assertEquals(FactFetchStatus.CURRENT, response.cloudForecast().status());
        assertEquals(FactFetchStatus.CURRENT, response.solarDarkness().status());
        assertEquals(NOW, response.solarDarkness().retrievedAtUtc());
        assertEquals(com.aurora.observation.dto.ForecastCoverage.Status.CANNOT_CHECK, response.coverage().status());
        assertEquals(RuleStatus.NOT_VALIDATED, response.outlook().ruleStatus());
    }

    @Test
    void reportsCurrentSourcesAndTheirIndependentForecastScopes() {
        OutlookService outlooks = mock(OutlookService.class);
        AuroraMapService aurora = mock(AuroraMapService.class);
        WeatherService weather = mock(WeatherService.class);
        when(outlooks.forLocation(DUBLIN.id())).thenReturn(outlook());
        when(aurora.forCoordinates(DUBLIN.latitude(), DUBLIN.longitude())).thenReturn(
                new LocalAuroraActivityResponse(com.aurora.observation.dto.ForecastStatus.CURRENT,
                        LocalAuroraActivityLevel.MEDIUM, 30, -6.0, 53.0, NOW.minusSeconds(600),
                        NOW.plusSeconds(1800), NOW, "NOAA OVATION", "ovation-local-v1"));
        when(weather.forecast(DUBLIN.latitude(), DUBLIN.longitude(), DUBLIN.timezone()))
                .thenReturn(new WeatherForecastResponse(NOW, NOW.plusSeconds(3600), "MET Norway",
                        DUBLIN.latitude(), DUBLIN.longitude(), List.of(
                        new com.aurora.observation.dto.WeatherCloudPoint(NOW.plusSeconds(300), 45.0))));

        ObservationFactsResponse response = service(outlooks, aurora, weather).forLocation(DUBLIN.id());

        assertEquals(FactFetchStatus.CURRENT, response.sourceStatus());
        assertEquals(NOW.minusSeconds(600), response.auroraActivity().scopeStartUtc());
        assertEquals(NOW.plusSeconds(1800), response.auroraActivity().scopeEndUtc());
        assertEquals(NOW.plusSeconds(300), response.cloudForecast().scopeStartUtc());
        assertEquals(NOW.plusSeconds(300), response.cloudForecast().scopeEndUtc());
        assertEquals(com.aurora.observation.dto.ForecastCoverage.Status.OVERLAPS, response.coverage().status());
        assertEquals(1, response.coverage().cloudPointsWithValuesInsideShortRange());
        assertEquals(com.aurora.observation.dto.FactTimeScope.SHORT_RANGE, response.auroraActivity().timeScope());
        assertEquals(com.aurora.observation.dto.FactTimeScope.TONIGHT, response.cloudForecast().timeScope());
        assertEquals(com.aurora.observation.dto.FactTimeScope.THREE_LOCAL_NIGHTS, response.solarDarkness().timeScope());
        assertEquals("https://www.spaceweather.gov/products/aurora-30-minute-forecast",
                response.auroraActivity().sourceUrl());
    }

    private ObservationFactsService service(OutlookService outlooks, AuroraMapService aurora, WeatherService weather) {
        return new ObservationFactsService(outlooks, aurora, weather, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private OutlookResponse outlook() {
        SolarDarkness darkness = new SolarDarkness(List.of(
                new ThresholdWindow(Threshold.CIVIL_TWILIGHT, -6, Status.NO_INTERVAL, List.of()),
                new ThresholdWindow(Threshold.NAUTICAL_TWILIGHT, -12, Status.NO_INTERVAL, List.of()),
                new ThresholdWindow(Threshold.ASTRONOMICAL_TWILIGHT, -18, Status.NO_INTERVAL, List.of())));
        return new OutlookResponse(DUBLIN, NOW, RuleStatus.NOT_VALIDATED, List.of(
                new NightOutlook(LocalDate.of(2026, 9, 25), "+01:00", NOW, NOW.plusSeconds(86_400),
                        OutlookLevel.INSUFFICIENT_DATA, "pending", darkness)));
    }
}
