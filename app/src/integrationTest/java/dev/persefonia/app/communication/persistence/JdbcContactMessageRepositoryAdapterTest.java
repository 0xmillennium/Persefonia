package dev.persefonia.app.communication.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.communication.domain.contact.AdminAccountId;
import dev.persefonia.communication.domain.contact.ContactBody;
import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.communication.domain.contact.ContactMessageStatusChangeId;
import dev.persefonia.communication.domain.contact.ContactSubject;
import dev.persefonia.communication.domain.contact.MailDeliveryStatus;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptId;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptResult;
import dev.persefonia.communication.domain.contact.SafeFailureReason;
import dev.persefonia.communication.domain.contact.SenderEmail;
import dev.persefonia.communication.domain.contact.SenderName;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class JdbcContactMessageRepositoryAdapterTest extends CommunicationPersistenceTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");

    @Test
    void savesAndFindsContactMessageRoundTrip() {
        ContactMessage message = message("first", NOW);

        contactMessages.save(message);

        ContactMessage reloaded = contactMessages.findById(message.id()).orElseThrow();
        assertThat(reloaded.id()).isEqualTo(message.id());
        assertThat(reloaded.senderName()).isEqualTo(SenderName.of("Ada first"));
        assertThat(reloaded.senderEmail()).isEqualTo(SenderEmail.of("ada.first@example.test"));
        assertThat(reloaded.subject()).isEqualTo(ContactSubject.of("Hello first"));
        assertThat(reloaded.body()).isEqualTo(ContactBody.of("Body first"));
        assertThat(reloaded.status()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(reloaded.mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.NOT_ATTEMPTED);
        assertThat(reloaded.version()).isZero();
    }

    @Test
    void missingLookupReturnsEmpty() {
        assertThat(contactMessages.findById(ContactMessageId.newId())).isEmpty();
    }

    @Test
    void persistsStatusChangeAndCurrentStatus() {
        ContactMessage message = message("status", NOW);
        contactMessages.save(message);
        ContactMessage reloaded = contactMessages.findById(message.id()).orElseThrow();

        reloaded.changeStatus(
                ContactMessageStatusChangeId.newId(),
                ContactMessageStatus.READ,
                AdminAccountId.newId(),
                NOW.plusSeconds(10));
        contactMessages.save(reloaded);

        ContactMessage updated = contactMessages.findById(message.id()).orElseThrow();
        assertThat(updated.status()).isEqualTo(ContactMessageStatus.READ);
        assertThat(updated.statusChanges()).hasSize(1);
        assertThat(updated.statusChanges().getFirst().previousStatus()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(updated.statusChanges().getFirst().newStatus()).isEqualTo(ContactMessageStatus.READ);
        assertThat(updated.version()).isEqualTo(1);
    }

    @Test
    void persistsSentAndFailedMailAttemptsAndCurrentDeliveryStatus() {
        ContactMessage message = message("mail", NOW);
        contactMessages.save(message);
        ContactMessage reloaded = contactMessages.findById(message.id()).orElseThrow();

        reloaded.recordMailSent(MailNotificationAttemptId.newId(), NOW.plusSeconds(10));
        reloaded.recordMailFailed(
                MailNotificationAttemptId.newId(),
                SafeFailureReason.of("SMTP unavailable"),
                NOW.plusSeconds(20));
        contactMessages.save(reloaded);

        ContactMessage updated = contactMessages.findById(message.id()).orElseThrow();
        assertThat(updated.mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.FAILED);
        assertThat(updated.mailNotificationAttempts())
                .extracting(attempt -> attempt.result())
                .containsExactly(MailNotificationAttemptResult.SENT, MailNotificationAttemptResult.FAILED);
        assertThat(updated.mailNotificationAttempts().get(1).failureReasonOptional())
                .contains(SafeFailureReason.of("SMTP unavailable"));
        assertThat(updated.status()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(updated.version()).isEqualTo(2);
    }

    @Test
    void replacesChildRowsWithoutDuplicatesAcrossSaves() {
        ContactMessage message = message("children", NOW);
        contactMessages.save(message);
        ContactMessage reloaded = contactMessages.findById(message.id()).orElseThrow();

        reloaded.recordMailSent(MailNotificationAttemptId.newId(), NOW.plusSeconds(10));
        contactMessages.save(reloaded);
        ContactMessage afterFirstUpdate = contactMessages.findById(message.id()).orElseThrow();
        afterFirstUpdate.changeStatus(
                ContactMessageStatusChangeId.newId(),
                ContactMessageStatus.READ,
                AdminAccountId.newId(),
                NOW.plusSeconds(20));
        contactMessages.save(afterFirstUpdate);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM communication.mail_notification_attempts",
                Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM communication.contact_message_status_changes",
                Long.class)).isEqualTo(1);
    }

    @Test
    void staleVersionIsRejected() {
        ContactMessage message = message("stale", NOW);
        contactMessages.save(message);
        ContactMessage first = contactMessages.findById(message.id()).orElseThrow();
        ContactMessage second = contactMessages.findById(message.id()).orElseThrow();

        first.changeStatus(
                ContactMessageStatusChangeId.newId(),
                ContactMessageStatus.READ,
                AdminAccountId.newId(),
                NOW.plusSeconds(10));
        contactMessages.save(first);
        second.changeStatus(
                ContactMessageStatusChangeId.newId(),
                ContactMessageStatus.SPAM,
                AdminAccountId.newId(),
                NOW.plusSeconds(11));

        assertThatThrownBy(() -> contactMessages.save(second))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void contactMessagesTableDoesNotContainForbiddenPrivacyColumns() {
        List<String> columns = jdbc.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'communication' AND table_name = 'contact_messages'
                """, String.class);

        assertThat(columns).doesNotContain(
                "raw_ip",
                "ip_address",
                "hashed_ip",
                "ip_hash",
                "user_agent",
                "user_agent_summary",
                "user_agent_hash",
                "rate_limit_key",
                "client_fingerprint",
                "session_id",
                "visitor_id",
                "tracking_cookie_id",
                "country_code");
    }

    static ContactMessage message(String key, Instant submittedAt) {
        return ContactMessage.create(
                ContactMessageId.from(UUID.nameUUIDFromBytes(("contact-" + key).getBytes())),
                SenderName.of("Ada " + key),
                SenderEmail.of("ada." + key + "@example.test"),
                ContactSubject.of("Hello " + key),
                ContactBody.of("Body " + key),
                submittedAt);
    }
}
