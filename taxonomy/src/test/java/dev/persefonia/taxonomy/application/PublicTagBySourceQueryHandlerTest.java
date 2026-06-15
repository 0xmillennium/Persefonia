package dev.persefonia.taxonomy.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.taxonomy.application.query.PublicTagBySourceQuery;
import dev.persefonia.taxonomy.application.query.PublicTagLookupResult;
import dev.persefonia.taxonomy.application.service.PublicTagBySourceQueryHandler;
import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagDescription;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagName;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PublicTagBySourceQueryHandlerTest {
    @Test
    void existingArchivedTagWithCurrentSlugIsFoundButStaleOrMissingTagIsNotFound() {
        Tag tag = Tag.create(
                TagId.newId(),
                TagName.of("Spring"),
                NormalizedTagName.ofCanonical("spring"),
                TagSlug.ofCanonical("spring"),
                TagDescription.ofNullable("Framework"),
                Instant.parse("2026-06-15T10:00:00Z"));
        tag.archive(Instant.parse("2026-06-15T10:01:00Z"));
        PublicTagBySourceQueryHandler handler = new PublicTagBySourceQueryHandler(repository(tag));

        assertThat(handler.lookup(new PublicTagBySourceQuery(tag.id().value(), "spring")))
                .isInstanceOfSatisfying(PublicTagLookupResult.Found.class,
                        found -> assertThat(found.tag().status()).isEqualTo("ARCHIVED"));
        assertThat(handler.lookup(new PublicTagBySourceQuery(tag.id().value(), "old-spring")))
                .isInstanceOf(PublicTagLookupResult.NotFound.class);
        assertThat(handler.lookup(new PublicTagBySourceQuery(TagId.newId().value(), "spring")))
                .isInstanceOf(PublicTagLookupResult.NotFound.class);
    }

    private static TagRepository repository(Tag tag) {
        return new TagRepository() {
            @Override public Tag save(Tag value) { return value; }
            @Override public Optional<Tag> findById(TagId id) {
                return tag.id().equals(id) ? Optional.of(tag) : Optional.empty();
            }
            @Override public Optional<Tag> findBySlug(TagSlug slug) { return Optional.empty(); }
            @Override public Optional<Tag> findByNormalizedName(NormalizedTagName name) { return Optional.empty(); }
            @Override public boolean existsBySlug(TagSlug slug) { return false; }
            @Override public boolean existsByNormalizedName(NormalizedTagName name) { return false; }
            @Override public List<Tag> findAllForAdmin() { return List.of(tag); }
        };
    }
}
