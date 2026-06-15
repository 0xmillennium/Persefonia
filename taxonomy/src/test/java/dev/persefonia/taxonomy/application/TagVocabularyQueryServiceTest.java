package dev.persefonia.taxonomy.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.taxonomy.application.service.TagVocabularyQueryService;
import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagDescription;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagName;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TagVocabularyQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");
    private final Tags tags = new Tags();
    private final TagVocabularyQueryService service = new TagVocabularyQueryService(tags);

    @Test
    void assignableLookupReturnsOnlyActiveTags() {
        Tag active = tags.save(tag("Active", "active"));
        Tag archived = tags.save(tag("Archived", "archived"));
        archived.archive(NOW.plusSeconds(1));

        assertThat(service.findAssignableTags()).extracting(item -> item.id()).containsExactly(active.id());
    }

    @Test
    void idLookupReturnsActiveAndArchivedAndOmitsMissing() {
        Tag active = tags.save(tag("Active", "active"));
        Tag archived = tags.save(tag("Archived", "archived"));
        archived.archive(NOW.plusSeconds(1));

        assertThat(service.findByIds(Set.of(active.id(), archived.id(), TagId.newId())))
                .extracting(item -> item.id())
                .containsExactlyInAnyOrder(active.id(), archived.id());
    }

    private static Tag tag(String name, String slug) {
        return Tag.create(
                TagId.newId(), TagName.of(name), NormalizedTagName.ofCanonical(name.toLowerCase()),
                TagSlug.ofCanonical(slug), TagDescription.empty(), NOW);
    }

    private static final class Tags implements TagRepository {
        private final Map<TagId, Tag> values = new LinkedHashMap<>();
        @Override public Tag save(Tag tag) { values.put(tag.id(), tag); return tag; }
        @Override public Optional<Tag> findById(TagId id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<Tag> findBySlug(TagSlug slug) { return Optional.empty(); }
        @Override public Optional<Tag> findByNormalizedName(NormalizedTagName name) { return Optional.empty(); }
        @Override public boolean existsBySlug(TagSlug slug) { return false; }
        @Override public boolean existsByNormalizedName(NormalizedTagName name) { return false; }
        @Override public List<Tag> findAllForAdmin() { return List.copyOf(values.values()); }
    }
}
