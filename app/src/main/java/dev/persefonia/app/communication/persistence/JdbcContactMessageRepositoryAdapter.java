package dev.persefonia.app.communication.persistence;

import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.domain.contact.AdminAccountId;
import dev.persefonia.communication.domain.contact.ContactBody;
import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.communication.domain.contact.ContactMessageStatusChange;
import dev.persefonia.communication.domain.contact.ContactMessageStatusChangeId;
import dev.persefonia.communication.domain.contact.ContactSubject;
import dev.persefonia.communication.domain.contact.MailDeliveryStatus;
import dev.persefonia.communication.domain.contact.MailNotificationAttempt;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptId;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptResult;
import dev.persefonia.communication.domain.contact.SafeFailureReason;
import dev.persefonia.communication.domain.contact.SenderEmail;
import dev.persefonia.communication.domain.contact.SenderName;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcContactMessageRepositoryAdapter implements ContactMessageRepository {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final ObjectProvider<TransactionTemplate> transactions;

    JdbcContactMessageRepositoryAdapter(
            ObjectProvider<NamedParameterJdbcTemplate> jdbc,
            ObjectProvider<TransactionTemplate> transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public void save(ContactMessage message) {
        Objects.requireNonNull(message, "message");
        transactionTemplate().executeWithoutResult(status -> {
            Optional<Long> currentVersion = currentVersion(message.id().value());
            if (currentVersion.isEmpty()) {
                insertMessage(message);
            } else {
                updateMessage(message, currentVersion.get());
            }
            replaceChildren(message);
        });
    }

    @Override
    public Optional<ContactMessage> findById(ContactMessageId id) {
        Objects.requireNonNull(id, "id");
        return jdbc().query("""
                SELECT id, sender_name, sender_email, subject, body, status, mail_delivery_status,
                       submitted_at, updated_at, version
                FROM communication.contact_messages
                WHERE id = :id
                """, Map.of("id", id.value()), (resultSet, rowNumber) -> {
            UUID messageId = resultSet.getObject("id", UUID.class);
            return ContactMessage.rehydrate(
                    ContactMessageId.from(messageId),
                    SenderName.of(resultSet.getString("sender_name")),
                    SenderEmail.of(resultSet.getString("sender_email")),
                    ContactSubject.of(resultSet.getString("subject")),
                    ContactBody.of(resultSet.getString("body")),
                    ContactMessageStatus.valueOf(resultSet.getString("status")),
                    MailDeliveryStatus.valueOf(resultSet.getString("mail_delivery_status")),
                    instant(resultSet, "submitted_at"),
                    instant(resultSet, "updated_at"),
                    resultSet.getLong("version"),
                    mailAttempts(messageId),
                    statusChanges(messageId));
        }).stream().findFirst();
    }

    private Optional<Long> currentVersion(UUID id) {
        return jdbc().query("""
                SELECT version
                FROM communication.contact_messages
                WHERE id = :id
                """, Map.of("id", id), (resultSet, rowNumber) -> resultSet.getLong("version"))
                .stream()
                .findFirst();
    }

    private void insertMessage(ContactMessage message) {
        jdbc().update("""
                INSERT INTO communication.contact_messages (
                    id, sender_name, sender_email, subject, body, status, mail_delivery_status,
                    submitted_at, updated_at, version
                ) VALUES (
                    :id, :senderName, :senderEmail, :subject, :body, :status, :mailDeliveryStatus,
                    :submittedAt, :updatedAt, :version
                )
                """, parameters(message));
    }

    private void updateMessage(ContactMessage message, long expectedVersion) {
        if (message.version() <= expectedVersion) {
            throw new OptimisticLockingFailureException("ContactMessage save is stale for id " + message.id().value());
        }
        int updated = jdbc().update("""
                UPDATE communication.contact_messages
                SET sender_name = :senderName,
                    sender_email = :senderEmail,
                    subject = :subject,
                    body = :body,
                    status = :status,
                    mail_delivery_status = :mailDeliveryStatus,
                    updated_at = :updatedAt,
                    version = :version
                WHERE id = :id AND version = :expectedVersion
                """, parameters(message).addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new OptimisticLockingFailureException("ContactMessage save is stale for id " + message.id().value());
        }
    }

    private MapSqlParameterSource parameters(ContactMessage message) {
        return new MapSqlParameterSource()
                .addValue("id", message.id().value())
                .addValue("senderName", message.senderName().value())
                .addValue("senderEmail", message.senderEmail().value())
                .addValue("subject", message.subject().value())
                .addValue("body", message.body().value())
                .addValue("status", message.status().name())
                .addValue("mailDeliveryStatus", message.mailDeliveryStatus().name())
                .addValue("submittedAt", Timestamp.from(message.submittedAt()))
                .addValue("updatedAt", Timestamp.from(message.updatedAt()))
                .addValue("version", message.version());
    }

    private void replaceChildren(ContactMessage message) {
        jdbc().update("""
                DELETE FROM communication.mail_notification_attempts
                WHERE contact_message_id = :messageId
                """, Map.of("messageId", message.id().value()));
        jdbc().update("""
                DELETE FROM communication.contact_message_status_changes
                WHERE contact_message_id = :messageId
                """, Map.of("messageId", message.id().value()));
        insertMailAttempts(message);
        insertStatusChanges(message);
    }

    private void insertMailAttempts(ContactMessage message) {
        MapSqlParameterSource[] batch = message.mailNotificationAttempts().stream()
                .map(attempt -> new MapSqlParameterSource()
                        .addValue("id", attempt.id().value())
                        .addValue("messageId", message.id().value())
                        .addValue("result", attempt.result().name())
                        .addValue("attemptedAt", Timestamp.from(attempt.attemptedAt()))
                        .addValue("failureReason", attempt.failureReasonOptional()
                                .map(reason -> reason.value())
                                .orElse(null)))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO communication.mail_notification_attempts (
                    id, contact_message_id, result, attempted_at, failure_reason
                ) VALUES (
                    :id, :messageId, :result, :attemptedAt, :failureReason
                )
                """, batch);
    }

    private void insertStatusChanges(ContactMessage message) {
        MapSqlParameterSource[] batch = message.statusChanges().stream()
                .map(change -> new MapSqlParameterSource()
                        .addValue("id", change.id().value())
                        .addValue("messageId", message.id().value())
                        .addValue("previousStatus", change.previousStatus().name())
                        .addValue("newStatus", change.newStatus().name())
                        .addValue("changedBy", change.changedBy().value())
                        .addValue("changedAt", Timestamp.from(change.changedAt())))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO communication.contact_message_status_changes (
                    id, contact_message_id, previous_status, new_status, changed_by_admin_id, changed_at
                ) VALUES (
                    :id, :messageId, :previousStatus, :newStatus, :changedBy, :changedAt
                )
                """, batch);
    }

    private List<MailNotificationAttempt> mailAttempts(UUID messageId) {
        return jdbc().query("""
                SELECT id, result, attempted_at, failure_reason
                FROM communication.mail_notification_attempts
                WHERE contact_message_id = :messageId
                ORDER BY attempted_at, id
                """, Map.of("messageId", messageId), (resultSet, rowNumber) -> {
            MailNotificationAttemptResult result = MailNotificationAttemptResult.valueOf(resultSet.getString("result"));
            SafeFailureReason reason = nullableFailureReason(resultSet);
            return new MailNotificationAttempt(
                    MailNotificationAttemptId.from(resultSet.getObject("id", UUID.class)),
                    result,
                    reason,
                    instant(resultSet, "attempted_at"));
        });
    }

    private List<ContactMessageStatusChange> statusChanges(UUID messageId) {
        return jdbc().query("""
                SELECT id, previous_status, new_status, changed_by_admin_id, changed_at
                FROM communication.contact_message_status_changes
                WHERE contact_message_id = :messageId
                ORDER BY changed_at, id
                """, Map.of("messageId", messageId), (resultSet, rowNumber) -> new ContactMessageStatusChange(
                ContactMessageStatusChangeId.from(resultSet.getObject("id", UUID.class)),
                ContactMessageStatus.valueOf(resultSet.getString("previous_status")),
                ContactMessageStatus.valueOf(resultSet.getString("new_status")),
                AdminAccountId.from(resultSet.getObject("changed_by_admin_id", UUID.class)),
                instant(resultSet, "changed_at")));
    }

    private static SafeFailureReason nullableFailureReason(ResultSet resultSet) throws SQLException {
        String value = resultSet.getString("failure_reason");
        return resultSet.wasNull() ? null : SafeFailureReason.of(value);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new CommunicationPersistenceException("JDBC contact message repository is not available.");
        }
        return available;
    }

    private TransactionTemplate transactionTemplate() {
        TransactionTemplate available = transactions.getIfAvailable();
        if (available == null) {
            throw new CommunicationPersistenceException("Transaction template is not available.");
        }
        return available;
    }
}
