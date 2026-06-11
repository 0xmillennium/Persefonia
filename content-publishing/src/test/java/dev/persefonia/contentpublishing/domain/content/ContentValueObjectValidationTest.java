package dev.persefonia.contentpublishing.domain.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContentValueObjectValidationTest {
    @Test
    void slugValidation() {
        assertThatThrownBy(() -> Slug.ofCanonical(" ")).isInstanceOf(ContentValidationException.class);
        assertThatThrownBy(() -> Slug.ofCanonical("Upper")).isInstanceOf(ContentValidationException.class);
        assertThatThrownBy(() -> Slug.ofCanonical("bad_slug")).isInstanceOf(ContentValidationException.class);
        assertThat(Slug.ofCanonical("valid-slug-123").value()).isEqualTo("valid-slug-123");
    }

    @Test
    void titleAndSummaryValidation() {
        assertThatThrownBy(() -> Title.of(" ")).isInstanceOf(ContentValidationException.class);
        assertThat(Title.of("  Valid title  ").value()).isEqualTo("Valid title");
        assertThatThrownBy(() -> Title.of("a".repeat(201))).isInstanceOf(ContentValidationException.class);

        assertThatThrownBy(() -> Summary.of(" ")).isInstanceOf(ContentValidationException.class);
        assertThatThrownBy(() -> Summary.of("a".repeat(501))).isInstanceOf(ContentValidationException.class);
    }

    @Test
    void markdownAndCanonicalPathValidation() {
        assertThatThrownBy(() -> MarkdownSource.of(" ")).isInstanceOf(ContentValidationException.class);
        assertThatThrownBy(() -> CanonicalPath.of(" ")).isInstanceOf(ContentValidationException.class);
        assertThatThrownBy(() -> CanonicalPath.of("articles/a")).isInstanceOf(ContentValidationException.class);
        assertThatThrownBy(() -> CanonicalPath.of("/articles/a b")).isInstanceOf(ContentValidationException.class);
    }

    @Test
    void numericValueObjectsValidateBounds() {
        assertThatThrownBy(() -> ReadingTime.minutes(0)).isInstanceOf(ContentValidationException.class);
        assertThatThrownBy(() -> ReadingTime.minutes(-1)).isInstanceOf(ContentValidationException.class);
        assertThatThrownBy(() -> SortOrder.of(-1)).isInstanceOf(ContentValidationException.class);
        assertThatThrownBy(() -> Version.of(-1)).isInstanceOf(ContentValidationException.class);
    }

    @Test
    void headingLevelAcceptsOnlyOneThroughSix() {
        for (int level = 1; level <= 6; level++) {
            assertThat(HeadingLevel.of(level).value()).isEqualTo(level);
        }
        assertThatThrownBy(() -> HeadingLevel.of(0)).isInstanceOf(ContentValidationException.class);
        assertThatThrownBy(() -> HeadingLevel.of(7)).isInstanceOf(ContentValidationException.class);
    }

    @Test
    void idWrappersRejectNullUuid() {
        assertThatThrownBy(() -> ContentId.from(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AssetId.from(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TagId.from(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdminIdentityRef.from(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ContentRevisionId.from(null)).isInstanceOf(NullPointerException.class);
        assertThat(ContentId.from(UUID.randomUUID()).value()).isNotNull();
    }
}
