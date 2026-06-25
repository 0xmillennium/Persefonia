package dev.persefonia.insights.application.service;

import dev.persefonia.insights.application.command.RecordInsightObservationCommand;
import dev.persefonia.insights.application.port.InsightsCounterRepository;
import dev.persefonia.insights.domain.model.InsightObservation;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

public final class RecordInsightObservationCommandService {
    private final InsightsCounterRepository counters;
    private final Clock clock;

    public RecordInsightObservationCommandService(InsightsCounterRepository counters, Clock clock) {
        this.counters = Objects.requireNonNull(counters, "counters must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void record(InsightObservation observation) {
        Objects.requireNonNull(observation, "observation must not be null");
        counters.increment(new RecordInsightObservationCommand(
                observation.metric(),
                observation.surface(),
                LocalDate.now(clock),
                observation.amount()));
    }
}
