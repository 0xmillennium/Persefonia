package dev.persefonia.app.communication.mail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.communication.application.port.ContactMessageNotification;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContactMailNotificationContentBuilderTest {
    private static final ContactMessageNotification NOTIFICATION = new ContactMessageNotification(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            Instant.parse("2026-06-25T10:00:00Z"),
            "Ada Lovelace",
            "ada@example.test",
            "Hello",
            "First line\nSecond line");

    private final ContactMailNotificationContentBuilder builder = new ContactMailNotificationContentBuilder();

    @Test
    void subjectIncludesPrefixAndSubmittedSubject() {
        String subject = builder.subject(NOTIFICATION, "[Persefonia Contact]");

        assertThat(subject).isEqualTo("[Persefonia Contact] Hello");
    }

    @Test
    void subjectSanitizesControlCharactersAndHeaderInjection() {
        var notification = new ContactMessageNotification(
                NOTIFICATION.contactMessageId(),
                NOTIFICATION.submittedAt(),
                NOTIFICATION.senderName(),
                NOTIFICATION.senderEmail(),
                "Hello\r\nBcc: attacker@example.test\u0007",
                NOTIFICATION.body());

        String subject = builder.subject(notification, "[Persefonia\r\nContact]");

        assertThat(subject)
                .doesNotContain("\r")
                .doesNotContain("\n")
                .doesNotContain("\u0007")
                .isEqualTo("[Persefonia Contact] Hello Bcc: attacker@example.test");
    }

    @Test
    void subjectIsTruncated() {
        var notification = new ContactMessageNotification(
                NOTIFICATION.contactMessageId(),
                NOTIFICATION.submittedAt(),
                NOTIFICATION.senderName(),
                NOTIFICATION.senderEmail(),
                "A".repeat(300),
                NOTIFICATION.body());

        assertThat(builder.subject(notification, "[Persefonia Contact]"))
                .hasSizeLessThanOrEqualTo(ContactMailNotificationContentBuilder.MAX_SUBJECT_LENGTH);
    }

    @Test
    void bodyIncludesOwnerNotificationFieldsWithoutAdminLink() {
        String body = builder.body(NOTIFICATION);

        assertThat(body)
                .contains("Contact message id: 11111111-1111-1111-1111-111111111111")
                .contains("Submitted at: 2026-06-25T10:00:00Z")
                .contains("Sender name: Ada Lovelace")
                .contains("Sender email: ada@example.test")
                .contains("Subject: Hello")
                .contains("First line\nSecond line")
                .doesNotContain("/admin/contact")
                .doesNotContain("http");
    }
}
