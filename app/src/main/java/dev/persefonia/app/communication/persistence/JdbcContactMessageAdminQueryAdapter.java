package dev.persefonia.app.communication.persistence;

import dev.persefonia.communication.application.query.ContactMessageAdminDetail;
import dev.persefonia.communication.application.query.ContactMessageAdminListItem;
import dev.persefonia.communication.application.query.ContactMessageAdminListPage;
import dev.persefonia.communication.application.query.ContactMessageAdminListRequest;
import dev.persefonia.communication.application.query.ContactMessageAdminMailAttemptItem;
import dev.persefonia.communication.application.query.ContactMessageAdminQueryService;
import dev.persefonia.communication.application.query.ContactMessageAdminStatusChangeItem;
import dev.persefonia.communication.domain.contact.AdminAccountId;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.communication.domain.contact.ContactMessageStatusChangeId;
import dev.persefonia.communication.domain.contact.MailDeliveryStatus;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptId;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptResult;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcContactMessageAdminQueryAdapter implements ContactMessageAdminQueryService {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    JdbcContactMessageAdminQueryAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public ContactMessageAdminListPage list(ContactMessageAdminListRequest request) {
        Objects.requireNonNull(request, "request");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("status", request.statusFilterOptional().map(status -> status.name()).orElse(null))
                .addValue("limit", request.pageSize())
                .addValue("offset", request.offset());

        List<ContactMessageAdminListItem> items = jdbc().query("""
                SELECT messages.id,
                       messages.sender_name,
                       messages.sender_email,
                       messages.subject,
                       messages.status,
                       messages.mail_delivery_status,
                       messages.submitted_at,
                       messages.updated_at,
                       COALESCE(attempt_summary.attempt_count, 0) AS mail_attempt_count,
                       latest_attempt.result AS latest_mail_attempt_result
                FROM communication.contact_messages messages
                LEFT JOIN LATERAL (
                    SELECT count(*) AS attempt_count
                    FROM communication.mail_notification_attempts attempts
                    WHERE attempts.contact_message_id = messages.id
                ) attempt_summary ON true
                LEFT JOIN LATERAL (
                    SELECT result
                    FROM communication.mail_notification_attempts attempts
                    WHERE attempts.contact_message_id = messages.id
                    ORDER BY attempted_at DESC, id DESC
                    LIMIT 1
                ) latest_attempt ON true
                WHERE (CAST(:status AS text) IS NULL OR messages.status = CAST(:status AS text))
                ORDER BY messages.submitted_at DESC, messages.id
                LIMIT :limit OFFSET :offset
                """, parameters, (resultSet, rowNumber) -> listItem(resultSet));

        Long totalItems = jdbc().queryForObject("""
                SELECT count(*)
                FROM communication.contact_messages
                WHERE (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
                """, parameters, Long.class);
        return new ContactMessageAdminListPage(
                items,
                request.page(),
                request.pageSize(),
                totalItems == null ? 0 : totalItems);
    }

    @Override
    public Optional<ContactMessageAdminDetail> findDetail(ContactMessageId id) {
        Objects.requireNonNull(id, "id");
        return jdbc().query("""
                SELECT id, sender_name, sender_email, subject, body, status, mail_delivery_status,
                       submitted_at, updated_at
                FROM communication.contact_messages
                WHERE id = :id
                """, Map.of("id", id.value()), (resultSet, rowNumber) -> {
            ContactMessageId messageId = ContactMessageId.from(resultSet.getObject("id", UUID.class));
            return new ContactMessageAdminDetail(
                    messageId,
                    resultSet.getString("sender_name"),
                    resultSet.getString("sender_email"),
                    resultSet.getString("subject"),
                    resultSet.getString("body"),
                    ContactMessageStatus.valueOf(resultSet.getString("status")),
                    MailDeliveryStatus.valueOf(resultSet.getString("mail_delivery_status")),
                    instant(resultSet, "submitted_at"),
                    instant(resultSet, "updated_at"),
                    mailAttempts(messageId),
                    statusChanges(messageId));
        }).stream().findFirst();
    }

    private static ContactMessageAdminListItem listItem(ResultSet resultSet) throws SQLException {
        String latestResult = resultSet.getString("latest_mail_attempt_result");
        MailNotificationAttemptResult latestMailAttemptResult = resultSet.wasNull()
                ? null
                : MailNotificationAttemptResult.valueOf(latestResult);
        return new ContactMessageAdminListItem(
                ContactMessageId.from(resultSet.getObject("id", UUID.class)),
                resultSet.getString("sender_name"),
                resultSet.getString("sender_email"),
                resultSet.getString("subject"),
                ContactMessageStatus.valueOf(resultSet.getString("status")),
                MailDeliveryStatus.valueOf(resultSet.getString("mail_delivery_status")),
                instant(resultSet, "submitted_at"),
                instant(resultSet, "updated_at"),
                resultSet.getLong("mail_attempt_count"),
                latestMailAttemptResult);
    }

    private List<ContactMessageAdminMailAttemptItem> mailAttempts(ContactMessageId messageId) {
        return jdbc().query("""
                SELECT id, result, failure_reason, attempted_at
                FROM communication.mail_notification_attempts
                WHERE contact_message_id = :messageId
                ORDER BY attempted_at DESC, id DESC
                """, Map.of("messageId", messageId.value()), (resultSet, rowNumber) -> {
            String failureReason = resultSet.getString("failure_reason");
            return new ContactMessageAdminMailAttemptItem(
                    MailNotificationAttemptId.from(resultSet.getObject("id", UUID.class)),
                    MailNotificationAttemptResult.valueOf(resultSet.getString("result")),
                    resultSet.wasNull() ? null : failureReason,
                    instant(resultSet, "attempted_at"));
        });
    }

    private List<ContactMessageAdminStatusChangeItem> statusChanges(ContactMessageId messageId) {
        return jdbc().query("""
                SELECT id, previous_status, new_status, changed_by_admin_id, changed_at
                FROM communication.contact_message_status_changes
                WHERE contact_message_id = :messageId
                ORDER BY changed_at DESC, id DESC
                """, Map.of("messageId", messageId.value()), (resultSet, rowNumber) ->
                new ContactMessageAdminStatusChangeItem(
                        ContactMessageStatusChangeId.from(resultSet.getObject("id", UUID.class)),
                        ContactMessageStatus.valueOf(resultSet.getString("previous_status")),
                        ContactMessageStatus.valueOf(resultSet.getString("new_status")),
                        AdminAccountId.from(resultSet.getObject("changed_by_admin_id", UUID.class)),
                        instant(resultSet, "changed_at")));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp.toInstant();
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new CommunicationPersistenceException("JDBC contact message admin query is not available.");
        }
        return available;
    }
}
