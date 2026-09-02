package dev.persefonia.communication.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.communication.application.authorization.ContactMessageCommandActor;
import dev.persefonia.communication.application.authorization.ContactMessageCommandAuthorizationPolicy;
import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.domain.contact.AdminAccountId;
import dev.persefonia.communication.domain.contact.ContactBody;
import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.communication.domain.contact.ContactSubject;
import dev.persefonia.communication.domain.contact.MailDeliveryStatus;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptId;
import dev.persefonia.communication.domain.contact.SafeFailureReason;
import dev.persefonia.communication.domain.contact.SenderEmail;
import dev.persefonia.communication.domain.contact.SenderName;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateContactMessageStatusCommandServiceTest {
    private static final Instant SUBMITTED_AT = Instant.parse("2026-06-25T10:00:00Z");
    private static final Instant CHANGED_AT = SUBMITTED_AT.plusSeconds(90);
    private static final UUID ADMIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final InMemoryContactMessages messages = new InMemoryContactMessages();
    private final OwnerOnlyPolicy authorization = new OwnerOnlyPolicy();
    private final UpdateContactMessageStatusCommandService service =
            new UpdateContactMessageStatusCommandService(messages, authorization);

    @Test
    void ownerCanMarkRead() {
        ContactMessage message = savedMessage();

        UpdateContactMessageStatusResult result = service.update(command(message.id(), ContactMessageStatus.READ));

        assertThat(result).isEqualTo(new UpdateContactMessageStatusResult.Updated(
                message.id(), ContactMessageStatus.NEW, ContactMessageStatus.READ));
        assertThat(messages.required(message.id()).status()).isEqualTo(ContactMessageStatus.READ);
    }

    @Test
    void ownerCanMarkReplied() {
        ContactMessage message = savedMessage();

        service.update(command(message.id(), ContactMessageStatus.REPLIED));

        assertThat(messages.required(message.id()).status()).isEqualTo(ContactMessageStatus.REPLIED);
    }

    @Test
    void ownerCanMarkSpam() {
        ContactMessage message = savedMessage();

        service.update(command(message.id(), ContactMessageStatus.SPAM));

        assertThat(messages.required(message.id()).status()).isEqualTo(ContactMessageStatus.SPAM);
    }

    @Test
    void ownerCanMarkArchived() {
        ContactMessage message = savedMessage();

        service.update(command(message.id(), ContactMessageStatus.ARCHIVED));

        assertThat(messages.required(message.id()).status()).isEqualTo(ContactMessageStatus.ARCHIVED);
    }

    @Test
    void statusChangeAppendsHistory() {
        ContactMessage message = savedMessage();

        service.update(command(message.id(), ContactMessageStatus.READ));

        assertThat(messages.required(message.id()).statusChanges()).hasSize(1);
        assertThat(messages.required(message.id()).statusChanges().getFirst().previousStatus())
                .isEqualTo(ContactMessageStatus.NEW);
        assertThat(messages.required(message.id()).statusChanges().getFirst().newStatus())
                .isEqualTo(ContactMessageStatus.READ);
        assertThat(messages.required(message.id()).statusChanges().getFirst().changedBy())
                .isEqualTo(AdminAccountId.from(ADMIN_ID));
        assertThat(messages.required(message.id()).updatedAt()).isEqualTo(CHANGED_AT);
    }

    @Test
    void sameStatusTransitionIsRejectedSafely() {
        ContactMessage message = savedMessage();
        service.update(command(message.id(), ContactMessageStatus.READ));

        UpdateContactMessageStatusResult result = service.update(command(message.id(), ContactMessageStatus.READ));

        assertThat(result).isInstanceOf(UpdateContactMessageStatusResult.Rejected.class);
        assertThat(messages.required(message.id()).statusChanges()).hasSize(1);
        assertThat(messages.saveCount()).isEqualTo(2);
    }

    @Test
    void missingMessageReturnsNotFound() {
        ContactMessageId missingId = ContactMessageId.newId();

        UpdateContactMessageStatusResult result = service.update(command(missingId, ContactMessageStatus.READ));

        assertThat(result).isEqualTo(new UpdateContactMessageStatusResult.NotFound(missingId));
        assertThat(messages.saveCount()).isZero();
    }

    @Test
    void mailDeliveryStatusAndAttemptsArePreserved() {
        ContactMessage message = savedMessage();
        message.recordMailFailed(
                MailNotificationAttemptId.newId(),
                SafeFailureReason.of("SMTP unavailable"),
                SUBMITTED_AT.plusSeconds(30));
        messages.save(message);

        service.update(command(message.id(), ContactMessageStatus.ARCHIVED));

        ContactMessage updated = messages.required(message.id());
        assertThat(updated.mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.FAILED);
        assertThat(updated.mailNotificationAttempts()).hasSize(1);
        assertThat(updated.mailNotificationAttempts().getFirst().failureReasonOptional())
                .contains(SafeFailureReason.of("SMTP unavailable"));
    }

    @Test
    void nonOwnerIsRejectedBeforeLoadingMessage() {
        ContactMessage message = savedMessage();

        assertThatThrownBy(() -> service.update(new UpdateContactMessageStatusCommand(
                new ContactMessageCommandActor(ADMIN_ID, true, false),
                message.id(),
                ContactMessageStatus.READ,
                CHANGED_AT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OWNER required");

        assertThat(messages.findCount()).isZero();
        assertThat(messages.required(message.id()).status()).isEqualTo(ContactMessageStatus.NEW);
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

    private static UpdateContactMessageStatusCommand command(
            ContactMessageId id,
            ContactMessageStatus status) {
        return new UpdateContactMessageStatusCommand(
                new ContactMessageCommandActor(ADMIN_ID, true, true),
                id,
                status,
                CHANGED_AT);
    }

    @Test
    void unexpectedRepositoryFailurePropagates() {
        ContactMessage message = savedMessage();
        messages.failOnFind = true;

        assertThatThrownBy(() -> service.update(command(message.id(), ContactMessageStatus.READ)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("repository unavailable");
    }

    private static final class OwnerOnlyPolicy implements ContactMessageCommandAuthorizationPolicy {
        @Override
        public void requireOwner(ContactMessageCommandActor actor, String commandName) {
            if (actor == null || !actor.active() || !actor.owner()) {
                throw new IllegalStateException("OWNER required");
            }
        }
    }

    private static final class InMemoryContactMessages implements ContactMessageRepository {
        private final Map<ContactMessageId, ContactMessage> messages = new LinkedHashMap<>();
        private int saves;
        private int finds;
        private boolean failOnFind;

        @Override
        public void save(ContactMessage message) {
            saves++;
            messages.put(message.id(), message);
        }

        @Override
        public Optional<ContactMessage> findById(ContactMessageId id) {
            finds++;
            if (failOnFind) {
                throw new IllegalStateException("repository unavailable");
            }
            return Optional.ofNullable(messages.get(id));
        }

        ContactMessage required(ContactMessageId id) {
            return messages.get(id);
        }

        int saveCount() {
            return saves;
        }

        int findCount() {
            return finds;
        }
    }
}
