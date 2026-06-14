package dev.persefonia.discovery.domain;

import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.List;
import java.util.Optional;

public interface DiscoverableResourceRepository {
    DiscoverableResource save(DiscoverableResource resource);

    DiscoverableResource replaceByKey(DiscoverableResource resource);

    Optional<DiscoverableResource> findById(DiscoverableResourceId id);

    Optional<DiscoverableResource> findByKey(DiscoverableResourceKey key);

    Optional<DiscoverableResource> findByPublicUrl(PublicUrl publicUrl);

    List<DiscoverableResource> findBySourceRef(SourceEntityRef sourceRef);

    int removeBySourceRef(SourceEntityRef sourceRef);
}
