package dev.persefonia.app.taxonomy.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

interface SpringDataTagRows extends CrudRepository<TagPersistenceEntity, UUID> {
    Optional<TagPersistenceEntity> findBySlug(String slug);
    Optional<TagPersistenceEntity> findByNormalizedName(String normalizedName);
    boolean existsBySlug(String slug);
    boolean existsByNormalizedName(String normalizedName);
}
