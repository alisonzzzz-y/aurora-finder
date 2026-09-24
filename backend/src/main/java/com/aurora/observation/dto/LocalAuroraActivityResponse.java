package com.aurora.observation.dto;

import java.time.Instant;

public record LocalAuroraActivityResponse(LocalAuroraActivityLevel level, int modelValue,
                                          double gridLongitude, double gridLatitude,
                                          Instant observationTime, Instant forecastTime,
                                          String source, String ruleVersion) {}
