package dev.persefonia.app.webpublic.tags;

import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PublicTagTestRepository implements TagRepository {
    private final Map<TagId, Tag> tags = new LinkedHashMap<>();

    @Override public Tag save(Tag tag) { tags.put(tag.id(), tag); return tag; }
    @Override public Optional<Tag> findById(TagId id) { return Optional.ofNullable(tags.get(id)); }
    @Override public Optional<Tag> findBySlug(TagSlug slug) {
        return tags.values().stream().filter(tag -> tag.slug().equals(slug)).findFirst();
    }
    @Override public Optional<Tag> findByNormalizedName(NormalizedTagName name) {
        return tags.values().stream().filter(tag -> tag.normalizedName().equals(name)).findFirst();
    }
    @Override public boolean existsBySlug(TagSlug slug) { return findBySlug(slug).isPresent(); }
    @Override public boolean existsByNormalizedName(NormalizedTagName name) { return findByNormalizedName(name).isPresent(); }
    @Override public List<Tag> findAllForAdmin() { return List.copyOf(tags.values()); }

    public void add(Tag tag) { tags.put(tag.id(), tag); }
    public void reset() { tags.clear(); }
}
