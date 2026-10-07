package com.aurora.observation.dto;

import java.time.Instant;
import java.util.List;

/** Transparent short-range heuristic, never a calibrated probability or whole-night forecast. */
public record ViewingConditions(OutlookLevel level, Instant evaluatedAtUtc, Instant validUntilUtc,
                                Integer modelSignal, Double cloudPercent, Boolean dark,
                                List<String> reasons, String ruleVersion, boolean probabilityCalibrated) {}
