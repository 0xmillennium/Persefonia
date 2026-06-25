package dev.persefonia.app.insights.application;

import dev.persefonia.insights.application.service.RecordInsightObservationCommandService;
import dev.persefonia.webpublic.insights.PublicInsightsObservationGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PublicInsightsObservationConfiguration {
    @Bean
    PublicInsightsObservationGateway publicInsightsObservationGateway(
            RecordInsightObservationCommandService observations) {
        return new SafePublicInsightsObservationGateway(observations);
    }
}
