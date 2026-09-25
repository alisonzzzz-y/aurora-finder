package com.aurora.observation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.Instant;

public record NightOutlook(LocalDate localDate, String utcOffsetAtStart,
                           Instant evaluationWindowStartUtc, Instant evaluationWindowEndUtc,
                           OutlookLevel level, OutlookReasonCode reasonCode, SolarDarkness solarDarkness) {
    @JsonProperty("reason")
    public String legacyReason() {
        return switch (reasonCode) {
            case RULES_NOT_VALIDATED -> "Aurora, cloud, and freshness rules are pending validation.";
        };
    }
}
