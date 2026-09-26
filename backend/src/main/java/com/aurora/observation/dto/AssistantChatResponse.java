package com.aurora.observation.dto;

import java.util.List;

public record AssistantChatResponse(String answer, String model, List<Location> locationCandidates) {
    public AssistantChatResponse(String answer, String model) {
        this(answer, model, List.of());
    }
}
