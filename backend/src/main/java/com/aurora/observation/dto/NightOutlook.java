package com.aurora.observation.dto;

import java.time.LocalDate;
import java.time.Instant;

public record NightOutlook(LocalDate localDate, String utcOffsetAtStart,
                           Instant evaluationWindowStartUtc, Instant evaluationWindowEndUtc,
                           OutlookLevel level, String reason) {}
