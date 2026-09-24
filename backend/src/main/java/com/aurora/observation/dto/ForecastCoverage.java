package com.aurora.observation.dto;

import java.time.Instant;

public record ForecastCoverage(Status status, FactTimeScope firstScope, FactTimeScope secondScope,
                              Instant shortRangeStartUtc, Instant shortRangeEndUtc,
                              int cloudPointsWithValuesInsideShortRange) {
    public enum Status {
        OVERLAPS,
        NO_OVERLAP,
        CANNOT_CHECK
    }
}
