package dev.persefonia.app.communication.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "persefonia.contact.mail")
public record ContactMailNotificationProperties(
        boolean enabled,
        String ownerRecipient,
        String from,
        String subjectPrefix,
        Boolean replyToSender) {
    private static final String DEFAULT_SUBJECT_PREFIX = "[Persefonia Contact]";

    public ContactMailNotificationProperties {
        ownerRecipient = normalize(ownerRecipient);
        from = normalize(from);
        subjectPrefix = normalize(subjectPrefix);
        if (subjectPrefix == null) {
            subjectPrefix = DEFAULT_SUBJECT_PREFIX;
        }
    }

    boolean hasRequiredDeliveryConfiguration() {
        return enabled && ownerRecipient != null && from != null;
    }

    boolean replyToSenderEnabled() {
        return replyToSender == null || replyToSender;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
