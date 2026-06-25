package dev.persefonia.insights.application.port;

import dev.persefonia.insights.application.command.RecordInsightObservationCommand;

public interface InsightsCounterRepository {
    void increment(RecordInsightObservationCommand command);
}
