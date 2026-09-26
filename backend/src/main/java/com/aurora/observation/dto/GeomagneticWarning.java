package com.aurora.observation.dto;

import java.time.Instant;

public record GeomagneticWarning(String productId, int expectedKIndex, String noaaScale,
                                 Instant validFrom, Instant validTo) {}
