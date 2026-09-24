package com.aurora.observation.dto;

import java.time.Instant;
import java.util.List;

public record OvationForecast(Instant observationTime, Instant forecastTime, String source,
                              List<OvationGridPoint> points) {}
