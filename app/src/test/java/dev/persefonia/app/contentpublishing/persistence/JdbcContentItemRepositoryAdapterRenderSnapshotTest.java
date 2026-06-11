package dev.persefonia.app.contentpublishing.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.domain.content.ContentItem;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JdbcContentItemRepositoryAdapterRenderSnapshotTest extends ContentPublishingRepositoryTestDatabase {
    @Test
    void savesAndLoadsRenderSnapshotAndOrderedHeadings() {
        ContentItem saved = contentItems.save(ContentItemRepositoryTestFixtures.published("render-round-trip",
                dev.persefonia.contentpublishing.domain.content.ContentVisibility.PUBLIC));

        var snapshot = contentItems.findById(saved.id()).orElseThrow().renderSnapshot().orElseThrow();
        assertThat(snapshot.renderedHtml().value()).contains("renderer-v1");
        assertThat(snapshot.rendererVersion().value()).isEqualTo("renderer-v1");
        assertThat(snapshot.readingTime().minutes()).isEqualTo(4);
        assertThat(snapshot.containsMermaid()).isTrue();
        assertThat(snapshot.headings())
                .extracting(heading -> heading.anchor().value())
                .containsExactly("intro", "details");
    }

    @Test
    void replacingRenderSnapshotRemovesOldHeadingRows() {
        ContentItem saved = contentItems.save(ContentItemRepositoryTestFixtures.published("replace-render",
                dev.persefonia.contentpublishing.domain.content.ContentVisibility.PUBLIC));
        ContentItem replacement = ContentItem.rehydrate(
                saved.id(),
                saved.type(),
                saved.status(),
                saved.visibility(),
                saved.language(),
                saved.slug().orElse(null),
                saved.title().orElse(null),
                saved.summary().orElse(null),
                saved.markdownSource().orElse(null),
                saved.metadata(),
                ContentItemRepositoryTestFixtures.snapshot(
                        "renderer-v2",
                        false,
                        ContentItemRepositoryTestFixtures.headings("new-intro", "new-details")),
                saved.tagIds(),
                saved.publishedAt().orElse(null),
                saved.unpublishedAt().orElse(null),
                saved.createdAt(),
                saved.updatedAt(),
                saved.version());

        contentItems.save(replacement);

        var reloaded = contentItems.findById(saved.id()).orElseThrow().renderSnapshot().orElseThrow();
        assertThat(reloaded.rendererVersion().value()).isEqualTo("renderer-v2");
        assertThat(reloaded.containsMermaid()).isFalse();
        assertThat(reloaded.headings()).extracting(heading -> heading.anchor().value())
                .containsExactly("new-intro", "new-details");
        assertThat(countHeading(saved.id().value(), "intro")).isZero();
    }

    @Test
    void savingWithoutRenderSnapshotRemovesExistingSnapshotAndHeadings() {
        ContentItem saved = contentItems.save(ContentItemRepositoryTestFixtures.published("remove-render",
                dev.persefonia.contentpublishing.domain.content.ContentVisibility.PUBLIC));

        contentItems.save(ContentItemRepositoryTestFixtures.withoutRenderSnapshot(saved));

        assertThat(contentItems.findById(saved.id()).orElseThrow().renderSnapshot()).isEmpty();
        assertThat(countHeadings(saved.id().value())).isZero();
    }

    private long countHeadings(java.util.UUID contentItemId) {
        return namedJdbc.queryForObject("""
                SELECT count(*)
                FROM publishing.content_rendered_headings
                WHERE content_item_id = :contentItemId
                """,
                Map.of("contentItemId", contentItemId),
                Long.class);
    }

    private long countHeading(java.util.UUID contentItemId, String anchor) {
        return namedJdbc.queryForObject("""
                SELECT count(*)
                FROM publishing.content_rendered_headings
                WHERE content_item_id = :contentItemId AND anchor = :anchor
                """,
                Map.of("contentItemId", contentItemId, "anchor", anchor),
                Long.class);
    }
}
