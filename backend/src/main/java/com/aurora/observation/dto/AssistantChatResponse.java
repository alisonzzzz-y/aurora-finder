package com.aurora.observation.dto;

import java.util.List;

public record AssistantChatResponse(String answer, String model, List<Location> locationCandidates, String runId, Long selectedLocationId) {
    public AssistantChatResponse(String answer, String model) {
        this(answer, model, List.of(), null, null);
    }

    public AssistantChatResponse(String answer, String model, List<Location> locationCandidates) {
        this(answer, model, locationCandidates, null, null);
    }
    public AssistantChatResponse(String answer, String model, List<Location> locationCandidates, String runId) {
        this(answer, model, locationCandidates, runId, null);
    }
}
