package dev.persefonia.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.audit.application.service.AuditRecordFactory;
import dev.persefonia.audit.application.service.AuditSafeValuePolicy;
import dev.persefonia.audit.domain.record.AuditActorType;
import dev.persefonia.audit.domain.record.AuditRecord;
import dev.persefonia.audit.domain.record.AuditValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuditRecordFactoryTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-25T10:00:05Z");

    private final AuditRecordFactory factory = new AuditRecordFactory(
            new AuditSafeValuePolicy(),
            Clock.fixed(CREATED_AT, ZoneOffset.UTC));

    @Test
    void safeCommandIsAccepted() {
        // The command itself constructs without error.
        assertThat(AuditCommands.safeAdminCommand()).isNotNull();
    }

    @Test
    void createsValidRecordPreservingOrderAndClockCreatedAt() {
        AuditRecord record = factory.create(AuditCommands.safeAdminCommand());

        assertThat(record.action().value()).isEqualTo("content.published");
        assertThat(record.actor().type()).isEqualTo(AuditActorType.ADMIN);
        assertThat(record.createdAt()).isEqualTo(CREATED_AT);
        assertThat(record.occurredAt()).isEqualTo(AuditCommands.OCCURRED_AT);
        assertThat(record.changes())
                .extracting(change -> change.fieldPath().value())
                .containsExactly("status", "title");
        assertThat(record.metadata())
                .extracting(entry -> entry.key().value())
                .containsExactly("reason");
    }

    @Test
    void createsValidSystemRecord() {
        AuditRecord record = factory.create(AuditCommands.safeSystemCommand());

        assertThat(record.actor().type()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(record.actor().context()).isEmpty();
        assertThat(record.requestId()).isEmpty();
    }

    @Test
    void createsContactStatusRecordWithDurableCommunicationVocabulary() {
        AuditRecord record = factory.create(AuditCommands.contactStatusChangedCommand());

        assertThat(record.action().value()).isEqualTo("contact_message.status.changed");
        assertThat(record.entity().context().value()).isEqualTo("communication");
        assertThat(record.entity().type().value()).isEqualTo("contact_message");
        assertThat(record.changes().getFirst().oldValue().value()).isEqualTo("NEW");
        assertThat(record.changes().getFirst().newValue().value()).isEqualTo("READ");
        assertThat(record.metadata().getFirst().value().value()).isEqualTo("owner_review");
    }

    @Test
    void rejectsUnsafeCommand() {
        assertThatThrownBy(() -> factory.create(AuditCommands.unsafeValueCommand()))
                .isInstanceOf(AuditValidationException.class);
    }
}
