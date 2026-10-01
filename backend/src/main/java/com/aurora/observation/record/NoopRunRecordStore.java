package com.aurora.observation.record;

import com.aurora.observation.dto.ObservationFactsResponse;
import java.util.Optional;

public class NoopRunRecordStore implements RunRecordStore {
    @Override public String begin(String kind, Long locationId) { return null; }
    @Override public void recordFacts(String runId, ObservationFactsResponse facts) {}
    @Override public void recordTool(String runId, int sequence, String name, String outcome, String evidenceJson) {}
    @Override public void finish(String runId, String status) {}
    @Override public Optional<RunRecord> find(String runId) { return Optional.empty(); }
}
