package dev.persefonia.app.taxonomy.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagDescription;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagName;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class JdbcTagRepositoryAdapterTest extends TaxonomyRepositoryTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");

    @Test
    void persistsLoadsFindsUpdatesArchivesAndListsTags() {
        Tag saved = tags.save(tag("Java", "java"));

        assertThat(tags.findById(saved.id())).isPresent();
        assertThat(tags.findBySlug(TagSlug.ofCanonical("java"))).isPresent();
        assertThat(tags.findByNormalizedName(NormalizedTagName.ofCanonical("java"))).isPresent();
        assertThat(tags.existsBySlug(TagSlug.ofCanonical("java"))).isTrue();
        assertThat(tags.existsByNormalizedName(NormalizedTagName.ofCanonical("java"))).isTrue();
        assertThat(tags.findAllForAdmin()).extracting(Tag::id).containsExactly(saved.id());

        saved.update(
                TagName.of("Java Platform"), NormalizedTagName.ofCanonical("java platform"),
                TagSlug.ofCanonical("java-platform"), TagDescription.ofNullable("Runtime"), NOW.plusSeconds(1));
        Tag updated = tags.save(saved);
        updated.archive(NOW.plusSeconds(2));
        Tag archived = tags.save(updated);

        assertThat(archived.status()).isEqualTo(TagStatus.ARCHIVED);
        assertThat(archived.version()).isEqualTo(2);
    }

    @Test
    void databaseEnforcesUniqueSlugAndNormalizedName() {
        tags.save(tag("Java", "java"));

        assertThatThrownBy(() -> tags.save(tag("JVM", "java")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> tags.save(Tag.create(
                        TagId.newId(), TagName.of("Other"), NormalizedTagName.ofCanonical("java"),
                        TagSlug.ofCanonical("other"), TagDescription.empty(), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Tag tag(String name, String slug) {
        return Tag.create(
                TagId.newId(), TagName.of(name), NormalizedTagName.ofCanonical(name.toLowerCase()),
                TagSlug.ofCanonical(slug), TagDescription.empty(), NOW);
    }
}
