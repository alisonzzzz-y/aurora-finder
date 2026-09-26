package com.aurora.observation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssistantChatRequest(
        @NotBlank @Size(max = 1000) String message,
        @Size(max = 10) String language,
        Long locationId,
        @Size(max = 10) List<@Valid AssistantMessage> history) {
}
