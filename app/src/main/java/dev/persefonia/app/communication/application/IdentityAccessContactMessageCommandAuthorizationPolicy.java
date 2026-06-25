package dev.persefonia.app.communication.application;

import dev.persefonia.communication.application.authorization.ContactMessageCommandActor;
import dev.persefonia.communication.application.authorization.ContactMessageCommandAuthorizationPolicy;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommand;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandActor;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import java.util.Objects;
import java.util.Set;

final class IdentityAccessContactMessageCommandAuthorizationPolicy implements ContactMessageCommandAuthorizationPolicy {
    private final AdminCommandAuthorizationPolicy delegate;

    IdentityAccessContactMessageCommandAuthorizationPolicy(AdminCommandAuthorizationPolicy delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void requireOwner(ContactMessageCommandActor actor, String commandName) {
        Objects.requireNonNull(actor, "actor");
        delegate.requireOwner(
                new AdminCommandActor(
                        AdminAccountId.of(actor.identityRef()),
                        actor.active() ? AdminAccountStatus.ACTIVE : AdminAccountStatus.DISABLED,
                        Set.of(actor.owner() ? AdminRole.OWNER : AdminRole.EDITOR)),
                AdminCommand.named(commandName));
    }
}
