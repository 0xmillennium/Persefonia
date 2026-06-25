package dev.persefonia.app.communication.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContactMailNotificationPropertiesTest {
    @Test
    void defaultsSubjectPrefixAndReplyToSender() {
        var properties = new ContactMailNotificationProperties(false, " ", " ", " ", null);

        assertThat(properties.subjectPrefix()).isEqualTo("[Persefonia Contact]");
        assertThat(properties.replyToSenderEnabled()).isTrue();
        assertThat(properties.hasRequiredDeliveryConfiguration()).isFalse();
    }

    @Test
    void requiresEnabledRecipientAndFromForDeliveryConfiguration() {
        assertThat(new ContactMailNotificationProperties(true, "owner@example.test", "site@example.test", null, true)
                .hasRequiredDeliveryConfiguration()).isTrue();
        assertThat(new ContactMailNotificationProperties(true, null, "site@example.test", null, true)
                .hasRequiredDeliveryConfiguration()).isFalse();
        assertThat(new ContactMailNotificationProperties(true, "owner@example.test", null, null, true)
                .hasRequiredDeliveryConfiguration()).isFalse();
    }
}
