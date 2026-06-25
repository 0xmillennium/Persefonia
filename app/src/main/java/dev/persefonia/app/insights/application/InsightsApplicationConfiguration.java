package dev.persefonia.app.insights.application;

import dev.persefonia.insights.application.port.InsightsCounterRepository;
import dev.persefonia.insights.application.service.RecordInsightObservationCommandService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class InsightsApplicationConfiguration {
    @Bean
    RecordInsightObservationCommandService recordInsightObservationCommandService(
            InsightsCounterRepository counters,
            Clock clock) {
        return new RecordInsightObservationCommandService(counters, clock);
    }
}
