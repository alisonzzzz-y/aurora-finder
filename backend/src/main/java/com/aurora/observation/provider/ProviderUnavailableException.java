package com.aurora.observation.provider;

public class ProviderUnavailableException extends RuntimeException {
    private final ProviderFailure failure;

    public ProviderUnavailableException(ProviderFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public ProviderUnavailableException(ProviderFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public ProviderFailure failure() {
        return failure;
    }
}
