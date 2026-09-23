package com.aurora.observation.dto;

import java.time.Instant;
import java.util.List;

public record OutlookResponse(Location location, Instant generatedAtUtc, String ruleStatus,
                              List<NightOutlook> nights) {}
