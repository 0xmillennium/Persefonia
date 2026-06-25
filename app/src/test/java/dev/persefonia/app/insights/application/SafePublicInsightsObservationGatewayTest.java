package dev.persefonia.app.insights.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.insights.application.command.RecordInsightObservationCommand;
import dev.persefonia.insights.application.port.InsightsCounterRepository;
import dev.persefonia.insights.application.service.RecordInsightObservationCommandService;
import dev.persefonia.insights.domain.model.InsightMetric;
import dev.persefonia.insights.domain.model.InsightSurface;
import dev.persefonia.webpublic.insights.PublicInsightSurface;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafePublicInsightsObservationGatewayTest {
    private final RecordingCounterRepository counters = new RecordingCounterRepository();
    private final SafePublicInsightsObservationGateway gateway = new SafePublicInsightsObservationGateway(
            new RecordInsightObservationCommandService(
                    counters,
                    Clock.fixed(Instant.parse("2026-06-25T10:00:00Z"), ZoneOffset.UTC)));

    @Test
    void recordsOnlyTypedPublicObservationCommands() {
        gateway.recordPageView(PublicInsightSurface.HOME);
        gateway.recordSearchSubmitted();
        gateway.recordCvViewed();
        gateway.recordCvDownloaded();
        gateway.recordContactSubmitted();
        gateway.recordNotFound();

        assertThat(counters.commands()).containsExactly(
                command(InsightMetric.PUBLIC_PAGE_VIEW, InsightSurface.HOME),
                command(InsightMetric.PUBLIC_SEARCH_SUBMITTED, InsightSurface.SEARCH),
                command(InsightMetric.PUBLIC_CV_VIEWED, InsightSurface.CV),
                command(InsightMetric.PUBLIC_CV_DOWNLOADED, InsightSurface.CV),
                command(InsightMetric.PUBLIC_CONTACT_SUBMITTED, InsightSurface.CONTACT),
                command(InsightMetric.PUBLIC_NOT_FOUND, InsightSurface.NOT_FOUND));
    }

    @Test
    void catchesObservationFailures() {
        counters.fail = true;

        gateway.recordPageView(PublicInsightSurface.CONTACT);

        assertThat(counters.commands()).isEmpty();
    }

    private static RecordInsightObservationCommand command(InsightMetric metric, InsightSurface surface) {
        return new RecordInsightObservationCommand(metric, surface, LocalDate.parse("2026-06-25"), 1);
    }

    private static final class RecordingCounterRepository implements InsightsCounterRepository {
        private final List<RecordInsightObservationCommand> commands = new ArrayList<>();
        private boolean fail;

        @Override
        public void increment(RecordInsightObservationCommand command) {
            if (fail) {
                throw new IllegalStateException("storage unavailable");
            }
            commands.add(command);
        }

        List<RecordInsightObservationCommand> commands() {
            return commands;
        }
    }
}
