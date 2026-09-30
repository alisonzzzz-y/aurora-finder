package com.aurora.observation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssistantChatRequest(
        @NotBlank @Size(max = 1000) String message,
        @Size(max = 10) String language,
        @Positive Long locationId,
        @Size(max = 10) List<@NotNull @Valid AssistantMessage> history) {
}
