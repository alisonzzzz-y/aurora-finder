package com.aurora.observation.dto;

import java.time.Instant;
import java.util.List;

public record GeomagneticStormForecastResponse(Instant retrievedAt, Instant issuedAt, String source,
                                               List<GeomagneticStormDay> days) {}
