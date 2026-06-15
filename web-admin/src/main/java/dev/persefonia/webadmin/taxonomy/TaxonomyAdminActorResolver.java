package dev.persefonia.webadmin.taxonomy;

import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandActor;
import org.springframework.security.core.Authentication;

public interface TaxonomyAdminActorResolver {
    TaxonomyCommandActor resolve(Authentication authentication);
}
