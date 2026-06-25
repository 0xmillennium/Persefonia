package dev.persefonia.app.communication.application;

import dev.persefonia.communication.application.authorization.ContactMessageCommandAuthorizationPolicy;
import dev.persefonia.communication.application.command.SubmitContactMessageCommandService;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommandService;
import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CommunicationApplicationConfiguration {
    @Bean
    SubmitContactMessageCommandService submitContactMessageCommandService(ContactMessageRepository messages) {
        return new SubmitContactMessageCommandService(messages);
    }

    @Bean
    ContactMessageCommandAuthorizationPolicy contactMessageCommandAuthorizationPolicy(
            AdminCommandAuthorizationPolicy policy) {
        return new IdentityAccessContactMessageCommandAuthorizationPolicy(policy);
    }

    @Bean
    UpdateContactMessageStatusCommandService updateContactMessageStatusCommandService(
            ContactMessageRepository messages,
            ContactMessageCommandAuthorizationPolicy authorization) {
        return new UpdateContactMessageStatusCommandService(messages, authorization);
    }
}
