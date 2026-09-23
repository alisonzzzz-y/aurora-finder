package com.aurora.observation.provider;

public enum ProviderFailure {
    DISABLED,
    INVALID_RESPONSE,
    TIMEOUT,
    RATE_LIMITED,
    UPSTREAM_ERROR,
    NETWORK_ERROR,
    INTERRUPTED
}
