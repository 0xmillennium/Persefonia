package dev.persefonia.app.taxonomy.application;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommand;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandActor;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandActor;
import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandAuthorizationPolicy;
import java.util.Objects;
import java.util.Set;

final class IdentityAccessTaxonomyCommandAuthorizationPolicy implements TaxonomyCommandAuthorizationPolicy {
    private final AdminCommandAuthorizationPolicy delegate;

    IdentityAccessTaxonomyCommandAuthorizationPolicy(AdminCommandAuthorizationPolicy delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void requireOwner(TaxonomyCommandActor actor, String commandName) {
        Objects.requireNonNull(actor, "actor");
        delegate.requireOwner(
                new AdminCommandActor(
                        AdminAccountId.of(actor.identityRef()),
                        actor.active() ? AdminAccountStatus.ACTIVE : AdminAccountStatus.DISABLED,
                        Set.of(actor.owner() ? AdminRole.OWNER : AdminRole.EDITOR)),
                AdminCommand.named(commandName));
    }
}
