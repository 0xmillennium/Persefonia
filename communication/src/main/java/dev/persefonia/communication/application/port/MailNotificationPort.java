package dev.persefonia.communication.application.port;

public interface MailNotificationPort {
    MailNotificationResult notifyOwner(ContactMessageNotification notification);
}
