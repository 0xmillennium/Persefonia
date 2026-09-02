package dev.persefonia.webadmin.discovery;

import dev.persefonia.discovery.application.authorization.AdminRedirectCommandActor;
import org.springframework.security.core.Authentication;

public interface AdminRedirectActorResolver {
    AdminRedirectCommandActor resolve(Authentication authentication);
}
