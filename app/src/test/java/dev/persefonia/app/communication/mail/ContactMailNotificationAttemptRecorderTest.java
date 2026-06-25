package dev.persefonia.app.communication.mail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.application.port.MailNotificationResult;
import dev.persefonia.communication.domain.contact.ContactBody;
import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.communication.domain.contact.ContactSubject;
import dev.persefonia.communication.domain.contact.MailDeliveryStatus;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptResult;
import dev.persefonia.communication.domain.contact.SenderEmail;
import dev.persefonia.communication.domain.contact.SenderName;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContactMailNotificationAttemptRecorderTest {
    private static final Instant SUBMITTED_AT = Instant.parse("2026-06-25T10:00:00Z");
    private static final Instant ATTEMPTED_AT = Instant.parse("2026-06-25T10:01:00Z");

    private final InMemoryContactMessages messages = new InMemoryContactMessages();
    private final ContactMailNotificationAttemptRecorder recorder = new ContactMailNotificationAttemptRecorder(
            messages,
            Clock.fixed(ATTEMPTED_AT, ZoneOffset.UTC));

    @Test
    void recordsSentAttemptAndKeepsMessageNew() {
        ContactMessage message = savedMessage();

        recorder.record(message.id(), MailNotificationResult.sent());

        ContactMessage reloaded = messages.findById(message.id()).orElseThrow();
        assertThat(reloaded.mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.SENT);
        assertThat(reloaded.status()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(reloaded.mailNotificationAttempts())
                .extracting(attempt -> attempt.result())
                .containsExactly(MailNotificationAttemptResult.SENT);
        assertThat(reloaded.mailNotificationAttempts().getFirst().attemptedAt()).isEqualTo(ATTEMPTED_AT);
    }

    @Test
    void recordsFailedAttemptWithSafeReasonAndKeepsMessageNew() {
        ContactMessage message = savedMessage();

        recorder.record(message.id(), MailNotificationResult.failed("mail_transport_unavailable"));

        ContactMessage reloaded = messages.findById(message.id()).orElseThrow();
        assertThat(reloaded.mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.FAILED);
        assertThat(reloaded.status()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(reloaded.mailNotificationAttempts())
                .extracting(attempt -> attempt.result())
                .containsExactly(MailNotificationAttemptResult.FAILED);
        assertThat(reloaded.mailNotificationAttempts().getFirst().failureReasonOptional())
                .get()
                .extracting(reason -> reason.value())
                .isEqualTo("mail_transport_unavailable");
    }

    @Test
    void unsafeFailureReasonIsReclassifiedBeforePersistence() {
        ContactMessage message = savedMessage();

        recorder.record(message.id(), MailNotificationResult.failed("ada@example.test private body stack trace"));

        assertThat(messages.findById(message.id()).orElseThrow().mailNotificationAttempts().getFirst()
                .failureReasonOptional())
                .get()
                .extracting(reason -> reason.value())
                .isEqualTo("unexpected_mail_failure");
    }

    @Test
    void missingContactMessageIsHandledSafely() {
        recorder.record(ContactMessageId.newId(), MailNotificationResult.sent());

        assertThat(messages.saved).isEmpty();
    }

    @Test
    void oneRecordingCallCreatesOneAttempt() {
        ContactMessage message = savedMessage();

        recorder.record(message.id(), MailNotificationResult.failed("mail_not_configured"));

        assertThat(messages.findById(message.id()).orElseThrow().mailNotificationAttempts()).hasSize(1);
    }

    private ContactMessage savedMessage() {
        ContactMessage message = ContactMessage.create(
                ContactMessageId.newId(),
                SenderName.of("Ada"),
                SenderEmail.of("ada@example.test"),
                ContactSubject.of("Hello"),
                ContactBody.of("Body"),
                SUBMITTED_AT);
        messages.save(message);
        return message;
    }

    private static final class InMemoryContactMessages implements ContactMessageRepository {
        private final Map<ContactMessageId, ContactMessage> saved = new LinkedHashMap<>();

        @Override
        public void save(ContactMessage message) {
            saved.put(message.id(), message);
        }

        @Override
        public Optional<ContactMessage> findById(ContactMessageId id) {
            return Optional.ofNullable(saved.get(id));
        }
    }
}
