package com.aurora.observation.dto;

import java.time.Instant;

public record ObservationFactsResponse(Instant generatedAtUtc, OutlookResponse outlook,
                                       SourceFact<LocalAuroraActivityResponse> auroraActivity,
                                       SourceFact<WeatherForecastResponse> cloudForecast,
                                       SourceFact<java.util.List<SolarNightFact>> solarDarkness,
                                       ForecastCoverage coverage,
                                       FactFetchStatus sourceStatus) {}
