package com.aurora.observation.dto;

import java.time.Instant;
import java.util.List;

public record AuroraMapResponse(Instant observationTime, Instant forecastTime, Instant retrievedAt,
                                String source, List<AuroraMapPoint> points) {}
