package dev.persefonia.app.security.admin;

import dev.persefonia.communication.application.authorization.ContactMessageCommandActor;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandActor;
import dev.persefonia.webadmin.contact.ContactMessageAdminActorResolver;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class PersefoniaContactMessageAdminActorResolver implements ContactMessageAdminActorResolver {
    private final PersefoniaAdminCommandActorResolver delegate;

    public PersefoniaContactMessageAdminActorResolver(PersefoniaAdminCommandActorResolver delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public ContactMessageCommandActor resolve(Authentication authentication) {
        AdminCommandActor actor = delegate.resolve(authentication);
        return new ContactMessageCommandActor(actor.accountId().value(), actor.isActive(), actor.isOwner());
    }
}
