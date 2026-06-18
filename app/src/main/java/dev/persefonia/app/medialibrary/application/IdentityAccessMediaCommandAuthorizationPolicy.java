package dev.persefonia.app.medialibrary.application;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommand;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandActor;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.medialibrary.application.authorization.MediaCommandActor;
import dev.persefonia.medialibrary.application.authorization.MediaCommandAuthorizationPolicy;
import java.util.Objects;
import java.util.Set;

final class IdentityAccessMediaCommandAuthorizationPolicy implements MediaCommandAuthorizationPolicy {
    private final AdminCommandAuthorizationPolicy delegate;

    IdentityAccessMediaCommandAuthorizationPolicy(AdminCommandAuthorizationPolicy delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void requireOwner(MediaCommandActor actor, String commandName) {
        Objects.requireNonNull(actor, "actor");
        delegate.requireOwner(
                new AdminCommandActor(
                        AdminAccountId.of(actor.identityRef()),
                        actor.active() ? AdminAccountStatus.ACTIVE : AdminAccountStatus.DISABLED,
                        Set.of(actor.owner() ? AdminRole.OWNER : AdminRole.EDITOR)),
                AdminCommand.named(commandName));
    }
}
