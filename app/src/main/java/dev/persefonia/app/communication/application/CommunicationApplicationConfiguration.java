package dev.persefonia.app.communication.application;

import dev.persefonia.communication.application.command.SubmitContactMessageCommandService;
import dev.persefonia.communication.application.port.ContactMessageRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CommunicationApplicationConfiguration {
    @Bean
    SubmitContactMessageCommandService submitContactMessageCommandService(ContactMessageRepository messages) {
        return new SubmitContactMessageCommandService(messages);
    }
}
