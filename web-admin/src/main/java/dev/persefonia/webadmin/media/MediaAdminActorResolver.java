package dev.persefonia.webadmin.media;

import dev.persefonia.medialibrary.application.authorization.MediaCommandActor;
import org.springframework.security.core.Authentication;

public interface MediaAdminActorResolver {
    MediaCommandActor resolve(Authentication authentication);
}
