package dev.persefonia.app.medialibrary.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationException;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.medialibrary.application.authorization.MediaCommandActor;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaCommandAuthorizationAdapterTest {
    private final IdentityAccessMediaCommandAuthorizationPolicy policy =
            new IdentityAccessMediaCommandAuthorizationPolicy(new AdminCommandAuthorizationPolicy());

    @Test
    void ownerActorIsAllowed() {
        assertThatCode(() -> policy.requireOwner(actor(true, true), "media.asset.upload"))
                .doesNotThrowAnyException();
    }

    @Test
    void nonOwnerInactiveAndMissingActorsAreDenied() {
        assertThatThrownBy(() -> policy.requireOwner(actor(true, false), "media.asset.upload"))
                .isInstanceOf(AdminCommandAuthorizationException.class);
        assertThatThrownBy(() -> policy.requireOwner(actor(false, true), "media.asset.update-metadata"))
                .isInstanceOf(AdminCommandAuthorizationException.class);
        assertThatThrownBy(() -> policy.requireOwner(null, "media.asset.upload"))
                .isInstanceOf(NullPointerException.class);
    }

    private static MediaCommandActor actor(boolean active, boolean owner) {
        return new MediaCommandActor(UUID.fromString("00000000-0000-0000-0000-000000000001"), active, owner);
    }
}
