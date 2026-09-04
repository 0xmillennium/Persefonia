package dev.persefonia.taxonomy.application.port;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.taxonomy.domain.model.TagId;
import java.util.List;
import java.util.Set;

public interface TagPublicRouteQuery {
    List<PublicUrl> findActiveRoutes(Set<TagId> tagIds, DiscoveryLanguage language, int limit);
}
