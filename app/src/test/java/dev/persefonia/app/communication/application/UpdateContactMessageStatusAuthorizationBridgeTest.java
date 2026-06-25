package dev.persefonia.app.communication.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.communication.application.authorization.ContactMessageCommandActor;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationException;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateContactMessageStatusAuthorizationBridgeTest {
    private final IdentityAccessContactMessageCommandAuthorizationPolicy policy =
            new IdentityAccessContactMessageCommandAuthorizationPolicy(new AdminCommandAuthorizationPolicy());

    @Test
    void activeOwnerIsAllowedToUpdateContactMessageStatus() {
        assertThatCode(() -> policy.requireOwner(
                new ContactMessageCommandActor(UUID.randomUUID(), true, true),
                "communication.contact-message.update-status"))
                .doesNotThrowAnyException();
    }

    @Test
    void nonOwnerIsRejectedForContactMessageStatusUpdate() {
        assertThatThrownBy(() -> policy.requireOwner(
                new ContactMessageCommandActor(UUID.randomUUID(), true, false),
                "communication.contact-message.update-status"))
                .isInstanceOf(AdminCommandAuthorizationException.class);
    }
}
