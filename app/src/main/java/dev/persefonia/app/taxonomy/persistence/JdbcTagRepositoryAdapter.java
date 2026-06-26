package dev.persefonia.app.taxonomy.persistence;

import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTagRepositoryAdapter implements TagRepository {
    private final ObjectProvider<SpringDataTagRows> rows;
    private final TagPersistenceMapper mapper = new TagPersistenceMapper();

    JdbcTagRepositoryAdapter(ObjectProvider<SpringDataTagRows> rows) {
        this.rows = Objects.requireNonNull(rows, "rows");
    }

    @Override
    public Tag save(Tag tag) {
        Objects.requireNonNull(tag, "tag");
        Optional<TagPersistenceEntity> existing = rows().findById(tag.id().value());
        Long jdbcVersion = existing.map(current -> versionForUpdate(tag, current)).orElse(null);
        TagPersistenceEntity saved = rows().save(mapper.toEntity(tag, jdbcVersion));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Tag> findById(TagId id) {
        return rows().findById(Objects.requireNonNull(id, "id").value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Tag> findBySlug(TagSlug slug) {
        return rows().findBySlug(Objects.requireNonNull(slug, "slug").value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Tag> findByNormalizedName(NormalizedTagName normalizedName) {
        return rows().findByNormalizedName(Objects.requireNonNull(normalizedName, "normalizedName").value())
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsBySlug(TagSlug slug) {
        return rows().existsBySlug(Objects.requireNonNull(slug, "slug").value());
    }

    @Override
    public boolean existsByNormalizedName(NormalizedTagName normalizedName) {
        return rows().existsByNormalizedName(Objects.requireNonNull(normalizedName, "normalizedName").value());
    }

    @Override
    public List<Tag> findAllForAdmin() {
        return java.util.stream.StreamSupport.stream(rows().findAll().spliterator(), false)
                .map(mapper::toDomain)
                .sorted(Comparator.comparing((Tag tag) -> tag.updatedAt()).reversed())
                .toList();
    }

    private Long versionForUpdate(Tag tag, TagPersistenceEntity current) {
        if (current.version() == null) {
            throw new TaxonomyPersistenceException("Existing tag has no optimistic lock version: " + tag.id().value());
        }
        if (tag.version() <= current.version()) {
            throw new OptimisticLockingFailureException("Tag save is stale for id " + tag.id().value());
        }
        return current.version();
    }

    private SpringDataTagRows rows() {
        SpringDataTagRows available = rows.getIfAvailable();
        if (available == null) {
            throw new TaxonomyPersistenceException("Spring Data JDBC taxonomy rows are not available.");
        }
        return available;
    }
}
