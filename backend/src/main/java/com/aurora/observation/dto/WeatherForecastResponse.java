package com.aurora.observation.dto;

import java.time.Instant;
import java.util.List;

public record WeatherForecastResponse(Instant retrievedAt, Instant expiresAt, String source,
                                      double requestedLatitude, double requestedLongitude,
                                      List<WeatherCloudPoint> cloudForecast) {}
