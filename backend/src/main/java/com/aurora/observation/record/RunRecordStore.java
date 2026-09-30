package com.aurora.observation.record;

import com.aurora.observation.dto.ObservationFactsResponse;
import java.util.Optional;

public interface RunRecordStore {
    String begin(String kind, Long locationId);

    void recordFacts(String runId, ObservationFactsResponse facts);

    void recordTool(String runId, int sequence, String name, String outcome, String evidenceJson);

    void finish(String runId, String status);

    Optional<RunRecord> find(String runId);
}
