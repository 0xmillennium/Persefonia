package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import org.springframework.security.core.Authentication;

public interface ContentAdminActorResolver {
    ContentCommandActor resolve(Authentication authentication);
}
