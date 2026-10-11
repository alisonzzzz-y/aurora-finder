package com.aurora.observation.dto;

import java.time.Instant;

/** Candidate distant model activity, not a visibility or whole-night probability. */
public record HorizonAurora(String status, String direction, Double distanceKm, Double bearingDegrees,
                            Integer modelSignal, Double latitude, Double longitude,
                            Instant forecastTime, String ruleVersion) {}
