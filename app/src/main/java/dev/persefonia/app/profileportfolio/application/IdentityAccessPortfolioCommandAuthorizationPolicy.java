package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommand;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandActor;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandAuthorizationPolicy;
import java.util.Objects;
import java.util.Set;

final class IdentityAccessPortfolioCommandAuthorizationPolicy implements PortfolioCommandAuthorizationPolicy {
    private final AdminCommandAuthorizationPolicy delegate;

    IdentityAccessPortfolioCommandAuthorizationPolicy(AdminCommandAuthorizationPolicy delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void requireOwner(PortfolioCommandActor actor, String commandName) {
        Objects.requireNonNull(actor, "actor");
        delegate.requireOwner(toIdentityActor(actor), AdminCommand.named(commandName));
    }

    private static AdminCommandActor toIdentityActor(PortfolioCommandActor actor) {
        return new AdminCommandActor(
                AdminAccountId.of(actor.identityRef()),
                actor.active() ? AdminAccountStatus.ACTIVE : AdminAccountStatus.DISABLED,
                Set.of(actor.owner() ? AdminRole.OWNER : AdminRole.EDITOR));
    }
}
