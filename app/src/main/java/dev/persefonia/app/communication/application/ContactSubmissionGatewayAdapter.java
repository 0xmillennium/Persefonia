package dev.persefonia.app.communication.application;

import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitKeyFactory;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitProperties;
import dev.persefonia.communication.application.command.SubmitContactMessageCommandService;
import dev.persefonia.platformoperations.application.port.RateLimitPort;
import dev.persefonia.webpublic.contact.PublicContactSubmissionGateway;
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
            Clock clock) {
        return new PublicContactSubmissionService(rateLimits, keyFactory, properties, commands, clock);
    }
}
