package dev.persefonia.webadmin.contact;

import dev.persefonia.communication.application.authorization.ContactMessageCommandActor;
import org.springframework.security.core.Authentication;

public interface ContactMessageAdminActorResolver {
    ContactMessageCommandActor resolve(Authentication authentication);
}
