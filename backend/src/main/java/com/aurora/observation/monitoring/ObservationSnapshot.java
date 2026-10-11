package com.aurora.observation.monitoring;

import java.time.Instant;
import com.aurora.observation.dto.ObservationFactsResponse;

/** Public city-level evidence only. Never contains run IDs, conversations or user identifiers. */
public record ObservationSnapshot(long locationId, String name, String timezone, Instant capturedAt,
        Integer modelValue, String auroraStatus, Instant auroraObservedAt, Instant auroraForecastAt,
        Double cloudPercent, Boolean dark, String skyLevel, Instant sampleAt, Instant validUntil,
        String ruleVersion, String cloudStatus, Instant cloudRetrievedAt) {
    public static ObservationSnapshot from(ObservationFactsResponse facts) {
        var place=facts.outlook().location(); var sky=facts.viewingConditions(); var aurora=facts.auroraActivity();
        return new ObservationSnapshot(place.id(),place.name(),place.timezone(),facts.generatedAtUtc(),
                aurora.data()==null?null:aurora.data().modelValue(),aurora.status().name(),aurora.sourceObservedAtUtc(),aurora.sourceForecastAtUtc(),
                sky==null?null:sky.cloudPercent(),sky==null?null:sky.dark(),sky==null?"INSUFFICIENT_DATA":sky.level().name(),
                sky==null?null:sky.evaluatedAtUtc(),sky==null?null:sky.validUntilUtc(),sky==null?"unknown":sky.ruleVersion(),
                facts.cloudForecast().status().name(),facts.cloudForecast().retrievedAtUtc());
    }
}
