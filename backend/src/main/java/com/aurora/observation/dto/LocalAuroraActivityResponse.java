package com.aurora.observation.dto;

import java.time.Instant;

public record LocalAuroraActivityResponse(ForecastStatus status, LocalAuroraActivityLevel level, Integer modelValue,
                                          Double gridLongitude, Double gridLatitude,
                                          Instant observationTime, Instant forecastTime, Instant retrievedAt,
                                          String source, String ruleVersion) {}
