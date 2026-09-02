package dev.persefonia.app.discovery.application;

import dev.persefonia.discovery.application.authorization.AdminRedirectCommandActor;
import dev.persefonia.discovery.application.authorization.AdminRedirectCommandAuthorizationPolicy;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommand;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandActor;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import java.util.Objects;
import java.util.Set;

public final class IdentityAccessAdminRedirectCommandAuthorizationPolicy
        implements AdminRedirectCommandAuthorizationPolicy {
    private final AdminCommandAuthorizationPolicy delegate;

    public IdentityAccessAdminRedirectCommandAuthorizationPolicy(AdminCommandAuthorizationPolicy delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void requireOwner(AdminRedirectCommandActor actor, String commandName) {
        Objects.requireNonNull(actor, "actor");
        delegate.requireOwner(
                new AdminCommandActor(
                        AdminAccountId.of(actor.identityRef()),
                        actor.active() ? AdminAccountStatus.ACTIVE : AdminAccountStatus.DISABLED,
                        Set.of(actor.owner() ? AdminRole.OWNER : AdminRole.EDITOR)),
                AdminCommand.named(commandName));
    }
}
