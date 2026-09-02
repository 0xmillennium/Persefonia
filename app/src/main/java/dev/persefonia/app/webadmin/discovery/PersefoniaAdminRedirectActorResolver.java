package dev.persefonia.app.webadmin.discovery;

import dev.persefonia.app.security.admin.PersefoniaAdminCommandActorResolver;
import dev.persefonia.discovery.application.authorization.AdminRedirectCommandActor;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandActor;
import dev.persefonia.webadmin.discovery.AdminRedirectActorResolver;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class PersefoniaAdminRedirectActorResolver implements AdminRedirectActorResolver {
    private final PersefoniaAdminCommandActorResolver delegate;

    public PersefoniaAdminRedirectActorResolver(PersefoniaAdminCommandActorResolver delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public AdminRedirectCommandActor resolve(Authentication authentication) {
        AdminCommandActor actor = delegate.resolve(authentication);
        return new AdminRedirectCommandActor(actor.accountId().value(), actor.isActive(), actor.isOwner());
    }
}
