package dev.persefonia.app.communication.mail;

import dev.persefonia.communication.application.port.ContactMessageNotification;
import dev.persefonia.communication.application.port.MailNotificationPort;
import dev.persefonia.communication.application.port.MailNotificationResult;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public final class SpringMailNotificationPortAdapter implements MailNotificationPort {
    static final String MAIL_NOT_CONFIGURED = "mail_not_configured";
    static final String MAIL_TRANSPORT_UNAVAILABLE = "mail_transport_unavailable";
    static final String MAIL_SENT_FAILED = "mail_sent_failed";
    static final String UNEXPECTED_MAIL_FAILURE = "unexpected_mail_failure";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final ContactMailNotificationProperties properties;
    private final ContactMailNotificationContentBuilder contentBuilder;

    SpringMailNotificationPortAdapter(
            ObjectProvider<JavaMailSender> mailSender,
            ContactMailNotificationProperties properties,
            ContactMailNotificationContentBuilder contentBuilder) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.contentBuilder = Objects.requireNonNull(contentBuilder, "contentBuilder must not be null");
    }

    @Override
    public MailNotificationResult notifyOwner(ContactMessageNotification notification) {
        Objects.requireNonNull(notification, "notification must not be null");
        JavaMailSender availableSender = mailSender.getIfAvailable();
        if (!properties.hasRequiredDeliveryConfiguration() || availableSender == null) {
            return MailNotificationResult.failed(MAIL_NOT_CONFIGURED);
        }

        ContactMailNotificationContent content = contentBuilder.build(notification, properties);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(properties.ownerRecipient());
        message.setFrom(properties.from());
        message.setSubject(content.subject());
        message.setText(content.body());
        if (properties.replyToSenderEnabled()) {
            message.setReplyTo(notification.senderEmail());
        }

        try {
            availableSender.send(message);
            return MailNotificationResult.sent();
        } catch (MailAuthenticationException | MailSendException exception) {
            return MailNotificationResult.failed(MAIL_TRANSPORT_UNAVAILABLE);
        } catch (MailException exception) {
            return MailNotificationResult.failed(MAIL_SENT_FAILED);
        } catch (RuntimeException exception) {
            return MailNotificationResult.failed(UNEXPECTED_MAIL_FAILURE);
        }
    }
}
