package dev.persefonia.insights.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.insights.application.command.RecordInsightObservationCommand;
import dev.persefonia.insights.application.port.InsightsCounterRepository;
import dev.persefonia.insights.domain.model.InsightMetric;
import dev.persefonia.insights.domain.model.InsightObservation;
import dev.persefonia.insights.domain.model.InsightSurface;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RecordInsightObservationCommandServiceTest {
    private final RecordingCounterRepository counters = new RecordingCounterRepository();
    private final RecordInsightObservationCommandService service = new RecordInsightObservationCommandService(
            counters,
            Clock.fixed(Instant.parse("2026-06-25T23:30:00Z"), ZoneOffset.UTC));

    @Test
    void recordsTypedObservationWithClockDateAndDefaultAmount() {
        service.record(InsightObservation.one(InsightMetric.PUBLIC_SEARCH_SUBMITTED, InsightSurface.SEARCH));

        assertThat(counters.lastCommand).isEqualTo(new RecordInsightObservationCommand(
                InsightMetric.PUBLIC_SEARCH_SUBMITTED,
                InsightSurface.SEARCH,
                LocalDate.parse("2026-06-25"),
                1));
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> new InsightObservation(
                InsightMetric.PUBLIC_PAGE_VIEW,
                InsightSurface.HOME,
                0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void commandRequiresTypedMetricSurfaceDateAndPositiveAmount() {
        assertThatThrownBy(() -> new RecordInsightObservationCommand(
                InsightMetric.PUBLIC_PAGE_VIEW,
                InsightSurface.HOME,
                LocalDate.parse("2026-06-25"),
                -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    private static final class RecordingCounterRepository implements InsightsCounterRepository {
        private RecordInsightObservationCommand lastCommand;

        @Override
        public void increment(RecordInsightObservationCommand command) {
            lastCommand = command;
        }
    }
}
