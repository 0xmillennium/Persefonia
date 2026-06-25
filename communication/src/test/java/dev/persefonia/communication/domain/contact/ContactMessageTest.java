package dev.persefonia.communication.domain.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContactMessageTest {
    private static final Instant SUBMITTED_AT = Instant.parse("2026-06-25T10:00:00Z");

    @Test
    void createsNewMessageWithInitialStatusAndTimestamps() {
        ContactMessage message = message();

        assertThat(message.status()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(message.mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.NOT_ATTEMPTED);
        assertThat(message.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(message.updatedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(message.version()).isZero();
        assertThat(message.mailNotificationAttempts()).isEmpty();
        assertThat(message.statusChanges()).isEmpty();
    }

    @Test
    void normalizesRequiredTextFields() {
        ContactMessage message = ContactMessage.create(
                ContactMessageId.newId(),
                SenderName.of(" Ada "),
                SenderEmail.of(" ADA@EXAMPLE.TEST "),
                ContactSubject.of(" Hello "),
                ContactBody.of(" Body\nline "),
                SUBMITTED_AT);

        assertThat(message.senderName().value()).isEqualTo("Ada");
        assertThat(message.senderEmail().value()).isEqualTo("ada@example.test");
        assertThat(message.subject().value()).isEqualTo("Hello");
        assertThat(message.body().value()).isEqualTo("Body\nline");
    }

    @Test
    void rejectsBlankRequiredFields() {
        assertThatThrownBy(() -> SenderName.of(" ")).isInstanceOf(ContactMessageValidationException.class);
        assertThatThrownBy(() -> SenderEmail.of(" ")).isInstanceOf(ContactMessageValidationException.class);
        assertThatThrownBy(() -> ContactSubject.of(" ")).isInstanceOf(ContactMessageValidationException.class);
        assertThatThrownBy(() -> ContactBody.of(" ")).isInstanceOf(ContactMessageValidationException.class);
    }

    @Test
    void rejectsInvalidEmail() {
        assertThatThrownBy(() -> SenderEmail.of("ada.example.test"))
                .isInstanceOf(ContactMessageValidationException.class)
                .hasMessage("sender email must be valid");
    }

    @Test
    void rejectsTooLongFields() {
        assertThatThrownBy(() -> SenderName.of("a".repeat(SenderName.MAX_LENGTH + 1)))
                .isInstanceOf(ContactMessageValidationException.class);
        assertThatThrownBy(() -> SenderEmail.of("a".repeat(245) + "@example.test"))
                .isInstanceOf(ContactMessageValidationException.class);
        assertThatThrownBy(() -> ContactSubject.of("a".repeat(ContactSubject.MAX_LENGTH + 1)))
                .isInstanceOf(ContactMessageValidationException.class);
        assertThatThrownBy(() -> ContactBody.of("a".repeat(ContactBody.MAX_LENGTH + 1)))
                .isInstanceOf(ContactMessageValidationException.class);
    }

    @Test
    void rejectsUnsafeControlCharactersButAllowsNormalBodyWhitespace() {
        assertThatThrownBy(() -> SenderName.of("Ada\u0000"))
                .isInstanceOf(ContactMessageValidationException.class);
        assertThatThrownBy(() -> ContactSubject.of("Hello\u0007"))
                .isInstanceOf(ContactMessageValidationException.class);
        assertThat(ContactBody.of("first line\nsecond line\tindented").value())
                .isEqualTo("first line\nsecond line\tindented");
    }

    @Test
    void fullBodyIsNotEchoedInValidationMessage() {
        String unsafeBody = "private details " + "\u0000" + " do not echo";

        assertThatThrownBy(() -> ContactBody.of(unsafeBody))
                .isInstanceOf(ContactMessageValidationException.class)
                .hasMessageNotContaining("private details")
                .hasMessageNotContaining("do not echo");
    }

    @Test
    void mailSentAttemptUpdatesDeliveryStatusAndVersion() {
        ContactMessage message = message();
        Instant attemptedAt = SUBMITTED_AT.plusSeconds(30);

        message.recordMailSent(MailNotificationAttemptId.newId(), attemptedAt);

        assertThat(message.mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.SENT);
        assertThat(message.mailNotificationAttempts())
                .extracting(MailNotificationAttempt::result)
                .containsExactly(MailNotificationAttemptResult.SENT);
        assertThat(message.updatedAt()).isEqualTo(attemptedAt);
        assertThat(message.version()).isEqualTo(1);
    }

    @Test
    void mailFailedAttemptUpdatesDeliveryStatusWithoutChangingContactStatus() {
        ContactMessage message = message();
        Instant attemptedAt = SUBMITTED_AT.plusSeconds(30);

        message.recordMailFailed(
                MailNotificationAttemptId.newId(),
                SafeFailureReason.of("SMTP unavailable"),
                attemptedAt);

        assertThat(message.status()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(message.mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.FAILED);
        assertThat(message.mailNotificationAttempts()).hasSize(1);
        assertThat(message.mailNotificationAttempts().getFirst().failureReasonOptional())
                .contains(SafeFailureReason.of("SMTP unavailable"));
    }

    @Test
    void validatesFailureReason() {
        assertThatThrownBy(() -> SafeFailureReason.of(" "))
                .isInstanceOf(ContactMessageValidationException.class);
        assertThatThrownBy(() -> SafeFailureReason.of("a".repeat(SafeFailureReason.MAX_LENGTH + 1)))
                .isInstanceOf(ContactMessageValidationException.class);
        assertThatThrownBy(() -> SafeFailureReason.of("unsafe\u0000reason"))
                .isInstanceOf(ContactMessageValidationException.class);
    }

    @Test
    void validStatusTransitionAppendsStatusChangeAndUpdatesTimestamp() {
        ContactMessage message = message();
        Instant changedAt = SUBMITTED_AT.plusSeconds(60);
        AdminAccountId adminId = AdminAccountId.newId();

        message.changeStatus(
                ContactMessageStatusChangeId.newId(),
                ContactMessageStatus.READ,
                adminId,
                changedAt);

        assertThat(message.status()).isEqualTo(ContactMessageStatus.READ);
        assertThat(message.statusChanges()).hasSize(1);
        assertThat(message.statusChanges().getFirst().previousStatus()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(message.statusChanges().getFirst().newStatus()).isEqualTo(ContactMessageStatus.READ);
        assertThat(message.statusChanges().getFirst().changedBy()).isEqualTo(adminId);
        assertThat(message.updatedAt()).isEqualTo(changedAt);
        assertThat(message.version()).isEqualTo(1);
    }

    @Test
    void sameStatusTransitionIsRejected() {
        ContactMessage message = message();

        assertThatThrownBy(() -> message.changeStatus(
                ContactMessageStatusChangeId.newId(),
                ContactMessageStatus.NEW,
                AdminAccountId.newId(),
                SUBMITTED_AT.plusSeconds(60)))
                .isInstanceOf(ContactMessageValidationException.class);
    }

    private static ContactMessage message() {
        return ContactMessage.create(
                ContactMessageId.newId(),
                SenderName.of("Ada"),
                SenderEmail.of("ada@example.test"),
                ContactSubject.of("Hello"),
                ContactBody.of("Body"),
                SUBMITTED_AT);
    }
}
