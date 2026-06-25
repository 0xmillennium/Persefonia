package dev.persefonia.insights.domain.model;

import java.util.Objects;

public record InsightObservation(
        InsightMetric metric,
        InsightSurface surface,
        int amount) {
    public InsightObservation {
        metric = Objects.requireNonNull(metric, "metric must not be null");
        surface = Objects.requireNonNull(surface, "surface must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    public static InsightObservation one(InsightMetric metric, InsightSurface surface) {
        return new InsightObservation(metric, surface, 1);
    }
}
