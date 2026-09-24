package com.aurora.observation.dto;

import java.time.Instant;

public record WeatherCloudPoint(Instant validAt, Double cloudCoverPercent) {}
