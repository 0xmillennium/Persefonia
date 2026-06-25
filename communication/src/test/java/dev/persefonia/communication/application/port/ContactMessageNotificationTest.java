package dev.persefonia.communication.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContactMessageNotificationTest {
    @Test
    void normalizesAndRequiresNotificationFields() {
        var notification = new ContactMessageNotification(
                UUID.randomUUID(),
                " Ada ",
                " ada@example.test ",
                " Hello ",
                " Message ");

        assertThat(notification.senderName()).isEqualTo("Ada");
        assertThat(notification.senderEmail()).isEqualTo("ada@example.test");
        assertThat(notification.subject()).isEqualTo("Hello");
        assertThat(notification.body()).isEqualTo("Message");
    }

    @Test
    void rejectsBlankRequiredFields() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new ContactMessageNotification(id, " ", "a@example.test", "Hi", "Body"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContactMessageNotification(id, "Ada", " ", "Hi", "Body"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContactMessageNotification(id, "Ada", "a@example.test", " ", "Body"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContactMessageNotification(id, "Ada", "a@example.test", "Hi", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
