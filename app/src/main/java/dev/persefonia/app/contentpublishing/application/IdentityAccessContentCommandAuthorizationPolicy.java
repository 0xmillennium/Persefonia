package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommand;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandActor;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import java.util.Objects;
import java.util.Set;

final class IdentityAccessContentCommandAuthorizationPolicy implements ContentCommandAuthorizationPolicy {
    private final AdminCommandAuthorizationPolicy delegate;

    IdentityAccessContentCommandAuthorizationPolicy(AdminCommandAuthorizationPolicy delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void requireOwner(ContentCommandActor actor, String commandName) {
        Objects.requireNonNull(actor, "actor");
        delegate.requireOwner(toIdentityActor(actor), AdminCommand.named(commandName));
    }

    private AdminCommandActor toIdentityActor(ContentCommandActor actor) {
        return new AdminCommandActor(
                AdminAccountId.of(actor.identityRef().value()),
                actor.active() ? AdminAccountStatus.ACTIVE : AdminAccountStatus.DISABLED,
                Set.of(actor.owner() ? AdminRole.OWNER : AdminRole.EDITOR));
    }
}
