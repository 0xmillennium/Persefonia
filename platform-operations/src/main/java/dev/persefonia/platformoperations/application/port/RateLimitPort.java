package dev.persefonia.platformoperations.application.port;

public interface RateLimitPort {
    RateLimitDecision checkAndConsume(RateLimitRequest request);
}
