package dev.persefonia.taxonomy.domain.port;

import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import java.util.List;
import java.util.Optional;

public interface TagRepository {
    Tag save(Tag tag);
    Optional<Tag> findById(TagId id);
    Optional<Tag> findBySlug(TagSlug slug);
    Optional<Tag> findByNormalizedName(NormalizedTagName normalizedName);
    boolean existsBySlug(TagSlug slug);
    boolean existsByNormalizedName(NormalizedTagName normalizedName);
    List<Tag> findAllForAdmin();
}
