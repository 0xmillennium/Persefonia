package dev.persefonia.app.communication.application;

import dev.persefonia.app.communication.mail.ContactMailNotificationAttemptRecorder;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitKeyFactory;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitProperties;
import dev.persefonia.app.transaction.PostCommitTaskExecutor;
import dev.persefonia.communication.application.command.SubmitContactMessageCommandService;
import dev.persefonia.communication.application.port.MailNotificationPort;
import dev.persefonia.platformoperations.application.port.RateLimitPort;
import dev.persefonia.webpublic.contact.PublicContactSubmissionGateway;
import dev.persefonia.webpublic.insights.PublicInsightsObservationGateway;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ContactSubmissionGatewayAdapter {
    @Bean
    PublicContactSubmissionGateway publicContactSubmissionGateway(
            RateLimitPort rateLimits,
            ContactRateLimitKeyFactory keyFactory,
            ContactRateLimitProperties properties,
            SubmitContactMessageCommandService commands,
            PostCommitTaskExecutor postCommitTasks,
            MailNotificationPort mailNotifications,
            ContactMailNotificationAttemptRecorder mailAttempts,
            PublicInsightsObservationGateway insights,
            Clock clock) {
        return new PublicContactSubmissionService(
                rateLimits,
                keyFactory,
                properties,
                commands,
                postCommitTasks,
                mailNotifications,
                mailAttempts,
                insights,
                clock);
    }
}
