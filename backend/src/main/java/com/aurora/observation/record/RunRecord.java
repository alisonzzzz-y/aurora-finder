package com.aurora.observation.record;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Database view for internal diagnostics. This is deliberately not a public API DTO. */
public record RunRecord(String id, String kind, Instant createdAtUtc, Instant completedAtUtc,
                        String resultStatus, Long locationId, String ruleVersion,
                        String ruleStatus, String coverageStatus,
                        List<Night> nights, List<Source> sources, List<ToolCall> toolCalls) {
    public record Night(int snapshot, Long locationId, LocalDate localDate, Instant windowStartUtc, Instant windowEndUtc,
                        String level, String reasonCode) {}

    public record Source(int snapshot, Long locationId, String key, String fetchStatus, String timeScope, String failureCode,
                         Instant retrievedAtUtc, Instant observedAtUtc, Instant forecastAtUtc,
                         Instant scopeStartUtc, Instant scopeEndUtc, String evidenceJson) {}

    public record ToolCall(int sequence, String name, Instant completedAtUtc,
                           String outcome, String evidenceJson) {}
}
