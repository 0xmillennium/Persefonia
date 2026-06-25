package dev.persefonia.communication.domain.contact;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class ContactMessage {
    private final ContactMessageId id;
    private final SenderName senderName;
    private final SenderEmail senderEmail;
    private final ContactSubject subject;
    private final ContactBody body;
    private ContactMessageStatus status;
    private MailDeliveryStatus mailDeliveryStatus;
    private final Instant submittedAt;
    private Instant updatedAt;
    private long version;
    private List<MailNotificationAttempt> mailNotificationAttempts;
    private List<ContactMessageStatusChange> statusChanges;

    private ContactMessage(
            ContactMessageId id,
            SenderName senderName,
            SenderEmail senderEmail,
            ContactSubject subject,
            ContactBody body,
            ContactMessageStatus status,
            MailDeliveryStatus mailDeliveryStatus,
            Instant submittedAt,
            Instant updatedAt,
            long version,
            List<MailNotificationAttempt> mailNotificationAttempts,
            List<ContactMessageStatusChange> statusChanges) {
        this.id = Objects.requireNonNull(id, "id");
        this.senderName = Objects.requireNonNull(senderName, "senderName");
        this.senderEmail = Objects.requireNonNull(senderEmail, "senderEmail");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.body = Objects.requireNonNull(body, "body");
        this.status = Objects.requireNonNull(status, "status");
        this.mailDeliveryStatus = Objects.requireNonNull(mailDeliveryStatus, "mailDeliveryStatus");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(submittedAt)) {
            throw new ContactMessageValidationException("updatedAt must not be before submittedAt");
        }
        if (version < 0) {
            throw new ContactMessageValidationException("version must not be negative");
        }
        this.version = version;
        this.mailNotificationAttempts = List.copyOf(Objects.requireNonNull(mailNotificationAttempts, "mailNotificationAttempts"));
        this.statusChanges = List.copyOf(Objects.requireNonNull(statusChanges, "statusChanges"));
    }

    public static ContactMessage create(
            ContactMessageId id,
            SenderName senderName,
            SenderEmail senderEmail,
            ContactSubject subject,
            ContactBody body,
            Instant submittedAt) {
        return new ContactMessage(
                id,
                senderName,
                senderEmail,
                subject,
                body,
                ContactMessageStatus.NEW,
                MailDeliveryStatus.NOT_ATTEMPTED,
                submittedAt,
                submittedAt,
                0,
                List.of(),
                List.of());
    }

    public static ContactMessage rehydrate(
            ContactMessageId id,
            SenderName senderName,
            SenderEmail senderEmail,
            ContactSubject subject,
            ContactBody body,
            ContactMessageStatus status,
            MailDeliveryStatus mailDeliveryStatus,
            Instant submittedAt,
            Instant updatedAt,
            long version,
            List<MailNotificationAttempt> mailNotificationAttempts,
            List<ContactMessageStatusChange> statusChanges) {
        return new ContactMessage(
                id, senderName, senderEmail, subject, body, status, mailDeliveryStatus, submittedAt, updatedAt,
                version, mailNotificationAttempts, statusChanges);
    }

    public void recordMailSent(MailNotificationAttemptId id, Instant attemptedAt) {
        mailDeliveryStatus = MailDeliveryStatus.SENT;
        appendMailAttempt(MailNotificationAttempt.sent(id, attemptedAt), attemptedAt);
    }

    public void recordMailFailed(MailNotificationAttemptId id, SafeFailureReason reason, Instant attemptedAt) {
        mailDeliveryStatus = MailDeliveryStatus.FAILED;
        appendMailAttempt(MailNotificationAttempt.failed(id, reason, attemptedAt), attemptedAt);
    }

    public void changeStatus(
            ContactMessageStatusChangeId id,
            ContactMessageStatus newStatus,
            AdminAccountId changedBy,
            Instant changedAt) {
        Objects.requireNonNull(newStatus, "newStatus");
        if (status == newStatus) {
            throw new ContactMessageValidationException("contact message status is already " + newStatus);
        }
        ContactMessageStatus previousStatus = status;
        status = newStatus;
        statusChanges = append(statusChanges, new ContactMessageStatusChange(id, previousStatus, newStatus, changedBy, changedAt));
        markUpdated(changedAt);
    }

    private void appendMailAttempt(MailNotificationAttempt attempt, Instant attemptedAt) {
        mailNotificationAttempts = append(mailNotificationAttempts, attempt);
        markUpdated(attemptedAt);
    }

    private static <T> List<T> append(List<T> existing, T value) {
        java.util.ArrayList<T> updated = new java.util.ArrayList<>(existing);
        updated.add(value);
        return List.copyOf(updated);
    }

    private void markUpdated(Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(submittedAt)) {
            throw new ContactMessageValidationException("updatedAt must not be before submittedAt");
        }
        updatedAt = now;
        version++;
    }

    public ContactMessageId id() {
        return id;
    }

    public SenderName senderName() {
        return senderName;
    }

    public SenderEmail senderEmail() {
        return senderEmail;
    }

    public ContactSubject subject() {
        return subject;
    }

    public ContactBody body() {
        return body;
    }

    public ContactMessageStatus status() {
        return status;
    }

    public MailDeliveryStatus mailDeliveryStatus() {
        return mailDeliveryStatus;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    public List<MailNotificationAttempt> mailNotificationAttempts() {
        return mailNotificationAttempts;
    }

    public List<ContactMessageStatusChange> statusChanges() {
        return statusChanges;
    }
}
