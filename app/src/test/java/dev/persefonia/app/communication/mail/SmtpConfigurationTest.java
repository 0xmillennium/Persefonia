package dev.persefonia.app.communication.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.application.port.MailNotificationPort;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpConfigurationTest {
    @TempDir Path temporary;

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withBean(ContactMessageRepository.class, () -> mock(ContactMessageRepository.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(ContactMailNotificationConfiguration.class);

    @Test
    void internalRelayDoesNotRequireCredentialsOrTls() {
        contexts.withPropertyValues(
                        "PERSEFONIA_SMTP_HOST=relay.internal.test",
                        "PERSEFONIA_SMTP_PORT=25",
                        "PERSEFONIA_CONTACT_MAIL_ENABLED=true",
                        "PERSEFONIA_CONTACT_MAIL_OWNER_RECIPIENT=owner@example.test",
                        "PERSEFONIA_CONTACT_MAIL_FROM=site@example.test")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(MailNotificationPort.class);
                    JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
                    assertThat(sender.getHost()).isEqualTo("relay.internal.test");
                    assertThat(sender.getPort()).isEqualTo(25);
                    assertThat(sender.getUsername()).isNullOrEmpty();
                    assertThat(sender.getPassword()).isNullOrEmpty();
                    assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.auth", "false")
                            .containsEntry("mail.smtp.starttls.enable", "false")
                            .containsEntry("mail.smtp.starttls.required", "false");
                    assertTimeouts(sender);
                });
    }

    @Test
    void authenticatedSubmissionReadsPasswordFromConfigtree() throws Exception {
        Path tree = Files.createDirectory(temporary.resolve("secrets"));
        Files.writeString(tree.resolve("spring.mail.password"), "fake-smtp-test-secret");
        contexts.withSystemProperties("spring.config.import=configtree:" + tree + "/")
                .withPropertyValues(
                        "PERSEFONIA_SMTP_HOST=mail.example.test",
                        "PERSEFONIA_SMTP_PORT=587",
                        "PERSEFONIA_SMTP_USERNAME=local-user",
                        "PERSEFONIA_SMTP_AUTH_ENABLED=true",
                        "PERSEFONIA_SMTP_STARTTLS_ENABLED=true",
                        "PERSEFONIA_SMTP_STARTTLS_REQUIRED=true",
                        "PERSEFONIA_CONTACT_MAIL_ENABLED=true",
                        "PERSEFONIA_CONTACT_MAIL_OWNER_RECIPIENT=owner@example.test",
                        "PERSEFONIA_CONTACT_MAIL_FROM=site@example.test")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(MailNotificationPort.class);
                    JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
                    assertThat(sender.getHost()).isEqualTo("mail.example.test");
                    assertThat(sender.getPort()).isEqualTo(587);
                    assertThat(sender.getUsername()).isEqualTo("local-user");
                    assertThat(sender.getPassword()).isEqualTo("fake-smtp-test-secret");
                    assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.auth", "true")
                            .containsEntry("mail.smtp.starttls.enable", "true")
                            .containsEntry("mail.smtp.starttls.required", "true");
                    assertTimeouts(sender);
                });
    }

    private static void assertTimeouts(JavaMailSenderImpl sender) {
        assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.connectiontimeout", "5000")
                .containsEntry("mail.smtp.timeout", "5000")
                .containsEntry("mail.smtp.writetimeout", "5000");
    }
}
