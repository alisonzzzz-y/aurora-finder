package com.aurora.observation.dto;

import java.time.Instant;
import java.util.List;

public record OutlookResponse(Location location, Instant generatedAtUtc, RuleStatus ruleStatus,
                              List<NightOutlook> nights) {}
