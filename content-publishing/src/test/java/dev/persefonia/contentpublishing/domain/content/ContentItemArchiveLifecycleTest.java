package dev.persefonia.contentpublishing.domain.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContentItemArchiveLifecycleTest {
    private static final Instant ARCHIVED_AT = ContentItemTestFixtures.PUBLISHED_AT.plusSeconds(120);

    @Test
    void archiveChangesStatusAndRemovesPublicEligibility() {
        ContentItem item = ContentItemTestFixtures.draft();

        item.archive(ARCHIVED_AT);

        assertThat(item.status()).isEqualTo(ContentStatus.ARCHIVED);
        assertThat(item.updatedAt()).isEqualTo(ARCHIVED_AT);
        assertThat(item.isPubliclyRenderable()).isFalse();
        assertThat(item.isListedPublicly()).isFalse();
        assertThat(item.isDirectUrlEligible()).isFalse();
    }

    @Test
    void archiveFromPublishedPreservesPublishedAtAndSetsUnpublishedAtWhenAbsent() {
        ContentItem item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);

        item.archive(ARCHIVED_AT);

        assertThat(item.publishedAt()).contains(ContentItemTestFixtures.PUBLISHED_AT);
        assertThat(item.unpublishedAt()).contains(ARCHIVED_AT);
        assertThat(item.isPubliclyRenderable()).isFalse();
    }

    @Test
    void archiveIsIdempotentAndKeepsHistoricalUnpublishedAtStable() {
        ContentItem item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        item.archive(ARCHIVED_AT);

        item.archive(ARCHIVED_AT.plusSeconds(60));

        assertThat(item.status()).isEqualTo(ContentStatus.ARCHIVED);
        assertThat(item.publishedAt()).contains(ContentItemTestFixtures.PUBLISHED_AT);
        assertThat(item.unpublishedAt()).contains(ARCHIVED_AT);
        assertThat(item.updatedAt()).isEqualTo(ARCHIVED_AT.plusSeconds(60));
    }

    @Test
    void publishingAndEditingArchivedContentIsRejected() {
        ContentItem item = ContentItemTestFixtures.completeDraft();
        item.archive(ARCHIVED_AT);

        assertThatThrownBy(() -> item.publish(ContentItemTestFixtures.renderSnapshot(), ARCHIVED_AT.plusSeconds(1)))
                .isInstanceOf(ContentLifecycleException.class);
        assertThatThrownBy(() -> item.changeTitle(Title.of("Archived edit"), ARCHIVED_AT.plusSeconds(1)))
                .isInstanceOf(ContentLifecycleException.class);
    }
}
