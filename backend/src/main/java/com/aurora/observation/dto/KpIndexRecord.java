package com.aurora.observation.dto;

import java.time.Instant;

public record KpIndexRecord(Instant periodStart, double kp, KpIndexType type, String noaaScale,
                            AuroraActivityLevel activityLevel) {}
