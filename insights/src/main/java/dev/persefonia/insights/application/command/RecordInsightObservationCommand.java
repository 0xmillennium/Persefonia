package dev.persefonia.insights.application.command;

import dev.persefonia.insights.domain.model.InsightMetric;
import dev.persefonia.insights.domain.model.InsightSurface;
import java.time.LocalDate;
import java.util.Objects;

public record RecordInsightObservationCommand(
        InsightMetric metric,
        InsightSurface surface,
        LocalDate date,
        int amount) {
    public RecordInsightObservationCommand {
        metric = Objects.requireNonNull(metric, "metric must not be null");
        surface = Objects.requireNonNull(surface, "surface must not be null");
        date = Objects.requireNonNull(date, "date must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
