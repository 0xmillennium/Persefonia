package dev.persefonia.app.webadmin.taxonomy;

import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class AdminTagTestRepository implements TagRepository {
    private final Map<TagId, Tag> values = new LinkedHashMap<>();

    @Override public Tag save(Tag tag) { values.put(tag.id(), tag); return tag; }
    @Override public Optional<Tag> findById(TagId id) { return Optional.ofNullable(values.get(id)); }
    @Override public Optional<Tag> findBySlug(TagSlug slug) {
        return values.values().stream().filter(tag -> tag.slug().equals(slug)).findFirst();
    }
    @Override public Optional<Tag> findByNormalizedName(NormalizedTagName name) {
        return values.values().stream().filter(tag -> tag.normalizedName().equals(name)).findFirst();
    }
    @Override public boolean existsBySlug(TagSlug slug) { return findBySlug(slug).isPresent(); }
    @Override public boolean existsByNormalizedName(NormalizedTagName name) { return findByNormalizedName(name).isPresent(); }
    @Override public List<Tag> findAllForAdmin() { return List.copyOf(values.values()); }
    void reset() { values.clear(); }
    List<Tag> all() { return List.copyOf(values.values()); }
}
