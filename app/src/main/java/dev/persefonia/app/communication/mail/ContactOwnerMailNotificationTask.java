package dev.persefonia.app.communication.mail;

import dev.persefonia.communication.application.port.ContactMessageNotification;
import dev.persefonia.communication.application.port.MailNotificationPort;
import dev.persefonia.communication.application.port.MailNotificationResult;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import java.util.Objects;

public final class ContactOwnerMailNotificationTask implements Runnable {
    private final ContactMessageNotification notification;
    private final MailNotificationPort mailNotifications;
    private final ContactMailNotificationAttemptRecorder attempts;

    public ContactOwnerMailNotificationTask(
            ContactMessageNotification notification,
            MailNotificationPort mailNotifications,
            ContactMailNotificationAttemptRecorder attempts) {
        this.notification = Objects.requireNonNull(notification, "notification must not be null");
        this.mailNotifications = Objects.requireNonNull(mailNotifications, "mailNotifications must not be null");
        this.attempts = Objects.requireNonNull(attempts, "attempts must not be null");
    }

    @Override
    public void run() {
        MailNotificationResult result;
        try {
            result = mailNotifications.notifyOwner(notification);
        } catch (RuntimeException exception) {
            result = MailNotificationResult.failed(SpringMailNotificationPortAdapter.UNEXPECTED_MAIL_FAILURE);
        }

        try {
            attempts.record(ContactMessageId.from(notification.contactMessageId()), result);
        } catch (RuntimeException exception) {
            // The submitted ContactMessage is already committed. Retry scheduling is intentionally out of scope.
        }
    }
}
