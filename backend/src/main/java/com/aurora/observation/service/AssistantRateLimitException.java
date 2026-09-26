package com.aurora.observation.service;

public class AssistantRateLimitException extends RuntimeException {
    public AssistantRateLimitException() {
        super("Too many AI requests. Please wait before trying again.");
    }
}
