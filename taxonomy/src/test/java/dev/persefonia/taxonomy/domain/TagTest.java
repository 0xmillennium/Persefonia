package dev.persefonia.taxonomy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagDescription;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagName;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import dev.persefonia.taxonomy.domain.model.TagValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TagTest {
    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");

    @Test
    void createsUpdatesAndArchivesTag() {
        Tag tag = tag();
        assertThat(tag.status()).isEqualTo(TagStatus.ACTIVE);

        tag.update(
                TagName.of("Architecture"),
                NormalizedTagName.ofCanonical("architecture"),
                TagSlug.ofCanonical("architecture"),
                TagDescription.ofNullable("  Decisions  "),
                NOW.plusSeconds(1));

        assertThat(tag.name().value()).isEqualTo("Architecture");
        assertThat(tag.description().value()).contains("Decisions");
        assertThat(tag.version()).isEqualTo(1);

        tag.archive(NOW.plusSeconds(2));
        long archivedVersion = tag.version();
        tag.archive(NOW.plusSeconds(3));

        assertThat(tag.status()).isEqualTo(TagStatus.ARCHIVED);
        assertThat(tag.version()).isEqualTo(archivedVersion);
        assertThat(tag.updatedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void validatesAndTrimsValueObjects() {
        assertThat(TagName.of("  Java  ").value()).isEqualTo("Java");
        assertThat(TagDescription.ofNullable("   ").value()).isEmpty();
        assertThatThrownBy(() -> TagName.of(" ")).isInstanceOf(TagValidationException.class);
        assertThatThrownBy(() -> TagSlug.ofCanonical("Not Valid")).isInstanceOf(TagValidationException.class);
    }

    private static Tag tag() {
        return Tag.create(
                TagId.newId(),
                TagName.of("Java"),
                NormalizedTagName.ofCanonical("java"),
                TagSlug.ofCanonical("java"),
                TagDescription.empty(),
                NOW);
    }
}
