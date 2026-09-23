package com.aurora.observation.dto;

import java.time.LocalDate;

public record NightOutlook(LocalDate localDate, String utcOffsetAtStart, OutlookLevel level, String reason) {}
