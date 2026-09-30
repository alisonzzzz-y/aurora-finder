package com.aurora.observation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AssistantMessage(
        @NotBlank @Pattern(regexp = "user|assistant") String role,
        @NotBlank @Size(max = 8000) String content,
        @Size(max = 10) List<@NotNull Location> locationCandidates) {
    public AssistantMessage(String role, String content) {
        this(role, content, List.of());
    }
}
