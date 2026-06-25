package dev.persefonia.app.communication.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.persefonia.communication.application.port.ContactMessageNotification;
import dev.persefonia.communication.application.port.MailNotificationResult;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SpringMailNotificationPortAdapterTest {
    private static final ContactMessageNotification NOTIFICATION = new ContactMessageNotification(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            Instant.parse("2026-06-25T10:00:00Z"),
            "Ada Lovelace",
            "ada@example.test",
            "Hello",
            "private body");

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final ObjectProvider<JavaMailSender> senderProvider = mockProvider(sender);
    private final ContactMailNotificationContentBuilder contentBuilder = new ContactMailNotificationContentBuilder();

    @Test
    void successfulSendReturnsSentAndSetsMessageFields() {
        var adapter = new SpringMailNotificationPortAdapter(senderProvider, configuredProperties(true), contentBuilder);

        MailNotificationResult result = adapter.notifyOwner(NOTIFICATION);

        assertThat(result.status()).isEqualTo(MailNotificationResult.Status.SENT);
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("owner@example.test");
        assertThat(message.getValue().getFrom()).isEqualTo("site@example.test");
        assertThat(message.getValue().getReplyTo()).isEqualTo("ada@example.test");
        assertThat(message.getValue().getSubject()).isEqualTo("[Persefonia Contact] Hello");
        assertThat(message.getValue().getText())
                .contains("Contact message id: 11111111-1111-1111-1111-111111111111")
                .contains("private body");
    }

    @Test
    void replyToCanBeDisabled() {
        var adapter = new SpringMailNotificationPortAdapter(senderProvider, configuredProperties(false), contentBuilder);

        adapter.notifyOwner(NOTIFICATION);

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getReplyTo()).isNull();
    }

    @Test
    void mailSenderFailureReturnsSafeFailedResult() {
        org.mockito.Mockito.doThrow(new MailSendException("smtp password body ada@example.test"))
                .when(sender).send(any(SimpleMailMessage.class));
        var adapter = new SpringMailNotificationPortAdapter(senderProvider, configuredProperties(true), contentBuilder);

        MailNotificationResult result = adapter.notifyOwner(NOTIFICATION);

        assertThat(result.status()).isEqualTo(MailNotificationResult.Status.FAILED);
        assertThat(result.failureReason()).isEqualTo(SpringMailNotificationPortAdapter.MAIL_TRANSPORT_UNAVAILABLE);
        assertThat(result.failureReason())
                .doesNotContain("private body")
                .doesNotContain("ada@example.test")
                .doesNotContain("password")
                .doesNotContain("MailSendException");
    }

    @Test
    void disabledOrMissingConfigurationReturnsFailedWithoutSending() {
        var adapter = new SpringMailNotificationPortAdapter(
                senderProvider,
                new ContactMailNotificationProperties(false, "owner@example.test", "site@example.test", null, true),
                contentBuilder);

        MailNotificationResult result = adapter.notifyOwner(NOTIFICATION);

        assertThat(result.status()).isEqualTo(MailNotificationResult.Status.FAILED);
        assertThat(result.failureReason()).isEqualTo(SpringMailNotificationPortAdapter.MAIL_NOT_CONFIGURED);
        verifyNoInteractions(sender);
    }

    @Test
    void missingJavaMailSenderReturnsFailedWithoutSending() {
        var emptyProvider = mockProvider(null);
        var adapter = new SpringMailNotificationPortAdapter(emptyProvider, configuredProperties(true), contentBuilder);

        MailNotificationResult result = adapter.notifyOwner(NOTIFICATION);

        assertThat(result.status()).isEqualTo(MailNotificationResult.Status.FAILED);
        assertThat(result.failureReason()).isEqualTo(SpringMailNotificationPortAdapter.MAIL_NOT_CONFIGURED);
    }

    private static ContactMailNotificationProperties configuredProperties(boolean replyToSender) {
        return new ContactMailNotificationProperties(
                true,
                "owner@example.test",
                "site@example.test",
                "[Persefonia Contact]",
                replyToSender);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> mockProvider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }
}
