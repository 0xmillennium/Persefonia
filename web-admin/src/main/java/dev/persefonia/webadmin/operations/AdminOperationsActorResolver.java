package dev.persefonia.webadmin.operations;

import dev.persefonia.platformoperations.application.operations.CacheOperationsCommandActor;
import org.springframework.security.core.Authentication;

public interface AdminOperationsActorResolver {
    CacheOperationsCommandActor resolve(Authentication authentication);
}
