package com.aurora.observation.dto;

import java.time.Instant;

public record ObservationFactsResponse(Instant generatedAtUtc, OutlookResponse outlook,
                                       SourceFact<LocalAuroraActivityResponse> auroraActivity,
                                       SourceFact<WeatherForecastResponse> cloudForecast,
                                       SourceFact<java.util.List<SolarNightFact>> solarDarkness,
                                       ForecastCoverage coverage,
                                       FactFetchStatus sourceStatus, String runId, ViewingConditions viewingConditions) {
    public ObservationFactsResponse(Instant generatedAtUtc, OutlookResponse outlook,
                                    SourceFact<LocalAuroraActivityResponse> auroraActivity,
                                    SourceFact<WeatherForecastResponse> cloudForecast,
                                    SourceFact<java.util.List<SolarNightFact>> solarDarkness,
                                    ForecastCoverage coverage, FactFetchStatus sourceStatus, String runId) {
        this(generatedAtUtc, outlook, auroraActivity, cloudForecast, solarDarkness, coverage, sourceStatus, runId, null);
    }
    public ObservationFactsResponse(Instant generatedAtUtc, OutlookResponse outlook,
                                    SourceFact<LocalAuroraActivityResponse> auroraActivity,
                                    SourceFact<WeatherForecastResponse> cloudForecast,
                                    SourceFact<java.util.List<SolarNightFact>> solarDarkness,
                                    ForecastCoverage coverage, FactFetchStatus sourceStatus) {
        this(generatedAtUtc, outlook, auroraActivity, cloudForecast, solarDarkness, coverage, sourceStatus, null);
    }
}
