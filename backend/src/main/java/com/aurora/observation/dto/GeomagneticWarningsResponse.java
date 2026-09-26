package com.aurora.observation.dto;

import java.time.Instant;
import java.util.List;

public record GeomagneticWarningsResponse(Instant retrievedAt, String source,
                                         List<GeomagneticWarning> warnings) {}
