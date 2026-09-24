package com.aurora.observation.dto;

import java.time.Instant;
import java.util.List;

/** Candidate solar-altitude windows only; these thresholds are not aurora-viewing rules. */
public record SolarDarkness(List<ThresholdWindow> thresholds) {
    public enum Threshold {
        CIVIL_TWILIGHT(-6.0),
        NAUTICAL_TWILIGHT(-12.0),
        ASTRONOMICAL_TWILIGHT(-18.0);

        private final double solarElevationDegrees;

        Threshold(double solarElevationDegrees) {
            this.solarElevationDegrees = solarElevationDegrees;
        }

        public double solarElevationDegrees() {
            return solarElevationDegrees;
        }
    }

    public enum Status {
        INTERVALS_FOUND,
        NO_INTERVAL,
        CONTINUOUS,
        CALCULATION_FAILED
    }

    public record ThresholdWindow(Threshold threshold, double solarElevationDegrees,
                                  Status status, List<TimeInterval> intervals) {}

    public record TimeInterval(Instant startUtc, Instant endUtc) {}
}
