package dev.persefonia.app.communication.mail;

import dev.persefonia.communication.application.port.MailNotificationPort;
import dev.persefonia.communication.application.port.ContactMessageRepository;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ContactMailNotificationProperties.class)
class ContactMailNotificationConfiguration {
    @Bean
    ContactMailNotificationContentBuilder contactMailNotificationContentBuilder() {
        return new ContactMailNotificationContentBuilder();
    }

    @Bean
    MailNotificationPort mailNotificationPort(
            ObjectProvider<JavaMailSender> mailSender,
            ContactMailNotificationProperties properties,
            ContactMailNotificationContentBuilder contentBuilder) {
        return new SpringMailNotificationPortAdapter(mailSender, properties, contentBuilder);
    }

    @Bean
    ContactMailNotificationAttemptRecorder contactMailNotificationAttemptRecorder(
            ContactMessageRepository messages,
            Clock clock) {
        return new ContactMailNotificationAttemptRecorder(messages, clock);
    }
}
