package dev.persefonia.app.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.communication.application.authorization.ContactMessageCommandActor;
import dev.persefonia.communication.application.command.ContactMessageStatusCommandGateway;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommand;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusResult;
import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.domain.contact.ContactBody;
import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.communication.domain.contact.ContactSubject;
import dev.persefonia.communication.domain.contact.SenderEmail;
import dev.persefonia.communication.domain.contact.SenderName;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import dev.persefonia.app.testsupport.SharedPostgresSpringIntegrationTest;

class TransactionalContactMessageStatusCommandGatewayTest extends SharedPostgresSpringIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-02T08:00:00Z");
    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final ContactMessageCommandActor OWNER = new ContactMessageCommandActor(OWNER_ID, true, true);

    @Autowired ContactMessageStatusCommandGateway gateway;
    @Autowired ContactMessageRepository messages;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    void gatewayImplementsFrameworkFreeContractAndIsTransactionallyProxied() {
        assertThat(gateway).isInstanceOf(ContactMessageStatusCommandGateway.class);
        assertThat(AopUtils.isAopProxy(gateway)).isTrue();
        assertThat(AopUtils.getTargetClass(gateway)).isEqualTo(TransactionalContactMessageStatusCommandGateway.class);
    }

    @Test
    void normalGatewayCallCommitsCurrentStatusAndOneHistoryRow() {
        ContactMessage message = saveMessage("commit");

        UpdateContactMessageStatusResult result = gateway.update(command(message.id(), OWNER));

        assertThat(result).isEqualTo(new UpdateContactMessageStatusResult.Updated(
                message.id(), ContactMessageStatus.NEW, ContactMessageStatus.READ));
        assertThat(messages.findById(message.id()).orElseThrow().status()).isEqualTo(ContactMessageStatus.READ);
        assertThat(historyCount(message.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_records WHERE action = 'contact_message.status.changed'",
                Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("""
                SELECT c.field_path, c.old_value, c.new_value
                FROM audit.audit_record_changes c
                JOIN audit.audit_records r ON r.id = c.audit_record_id
                WHERE r.action = 'contact_message.status.changed'
                """))
                .containsEntry("field_path", "status")
                .containsEntry("old_value", "NEW")
                .containsEntry("new_value", "READ");
    }

    @Test
    void outerRollbackRestoresStatusAndRemovesHistoryWrittenByRepositoryTransactionTemplate() {
        ContactMessage message = saveMessage("rollback");

        transactions.executeWithoutResult(status -> {
            gateway.update(command(message.id(), OWNER));
            assertThat(messages.findById(message.id()).orElseThrow().status()).isEqualTo(ContactMessageStatus.READ);
            assertThat(historyCount(message.id())).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(messages.findById(message.id()).orElseThrow().status()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(historyCount(message.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_records", Long.class)).isZero();
    }

    @Test
    void unauthorizedExecutionProducesNoSourceMutation() {
        ContactMessage message = saveMessage("unauthorized");
        ContactMessageCommandActor editor = new ContactMessageCommandActor(UUID.randomUUID(), true, false);

        assertThatThrownBy(() -> gateway.update(command(message.id(), editor)))
                .isInstanceOf(AdminCommandAuthorizationException.class);

        assertThat(messages.findById(message.id()).orElseThrow().status()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(historyCount(message.id())).isZero();
    }

    @Test
    void mandatoryAuditFailureRollsBackStatusAndHistory() {
        ContactMessage message = saveMessage("audit-failure");

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    jdbc.execute("""
                            ALTER TABLE audit.audit_records
                            ADD CONSTRAINT reject_contact_audit_test CHECK (false)
                            """);
                    gateway.update(command(message.id(), OWNER));
                }))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(messages.findById(message.id()).orElseThrow().status()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(historyCount(message.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_records", Long.class)).isZero();
    }

    private UpdateContactMessageStatusCommand command(
            ContactMessageId messageId,
            ContactMessageCommandActor actor) {
        return new UpdateContactMessageStatusCommand(actor, messageId, ContactMessageStatus.READ, NOW.plusSeconds(60));
    }

    private ContactMessage saveMessage(String key) {
        ContactMessage message = ContactMessage.create(
                ContactMessageId.from(UUID.nameUUIDFromBytes(("transaction-contact-" + key).getBytes())),
                SenderName.of("Ada " + key),
                SenderEmail.of("ada." + key + "@example.test"),
                ContactSubject.of("Transactional contact " + key),
                ContactBody.of("Body " + key),
                NOW);
        messages.save(message);
        return message;
    }

    private long historyCount(ContactMessageId messageId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM communication.contact_message_status_changes WHERE contact_message_id = ?",
                Long.class,
                messageId.value());
    }

}
