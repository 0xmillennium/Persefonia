package dev.persefonia.platformoperations.domain.cache;

public enum CachePurgeFailureReason {
    NETWORK_ERROR,
    TIMEOUT,
    RATE_LIMITED,
    PROVIDER_5XX,
    AUTHENTICATION_ERROR,
    INVALID_CONFIGURATION,
    INVALID_TARGET,
    UNKNOWN_PROVIDER_FAILURE
}
