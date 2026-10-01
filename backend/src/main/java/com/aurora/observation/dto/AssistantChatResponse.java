package com.aurora.observation.dto;

import java.util.List;

public record AssistantChatResponse(String answer, String model, List<Location> locationCandidates, String runId) {
    public AssistantChatResponse(String answer, String model) {
        this(answer, model, List.of(), null);
    }

    public AssistantChatResponse(String answer, String model, List<Location> locationCandidates) {
        this(answer, model, locationCandidates, null);
    }
}
