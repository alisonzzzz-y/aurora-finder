package com.aurora.observation.dto;

import com.aurora.observation.provider.ProviderFailure;

import java.time.Instant;

/** One source's result and provenance. Null timestamps/data mean the source did not provide them. */
public record SourceFact<T>(FactFetchStatus status, FactTimeScope timeScope, Instant retrievedAtUtc,
                            Instant sourceObservedAtUtc, Instant sourceForecastAtUtc,
                            Instant scopeStartUtc, Instant scopeEndUtc,
                            String source, String sourceUrl, ProviderFailure failureCode, T data) {}
