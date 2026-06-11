package dev.persefonia.app.contentpublishing.persistence.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;

class ContentItemJdbcAggregateSpikeTest extends ContentPublishingJdbcSpikeSupport {
    @Test
    void savesAndLoadsDraftContentItemRoot() {
        SpikeContentItem draft = draftItem();

        SpikeContentItem saved = adapter.saveContentItem(draft);
        SpikeContentItem loaded = adapter.findContentItem(draft.id()).orElseThrow();

        assertThat(saved.version()).isZero();
        assertThat(loaded.id()).isEqualTo(draft.id());
        assertThat(loaded.type()).isEqualTo(SpikeContentType.ARTICLE);
        assertThat(loaded.status()).isEqualTo(SpikeContentStatus.DRAFT);
        assertThat(loaded.visibility()).isEqualTo(SpikeContentVisibility.PRIVATE);
        assertThat(loaded.language()).isEqualTo(SpikeLanguage.EN);
        assertThat(loaded.slug()).isNull();
        assertThat(loaded.title()).isNull();
        assertThat(loaded.summary()).isNull();
        assertThat(loaded.markdownSource()).isNull();
        assertThat(loaded.renderSnapshot()).isNull();
        assertThat(loaded.version()).isZero();
    }

    @Test
    void savesAndLoadsPublishedContentItemWithRenderSnapshot() {
        SpikeContentItem published = publishedItem("spring-data-jdbc-spike")
                .withRenderSnapshot(snapshot("v1", List.of()));

        adapter.saveContentItem(published);

        SpikeContentItem loaded = adapter.findContentItem(published.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(SpikeContentStatus.PUBLISHED);
        assertThat(loaded.visibility()).isEqualTo(SpikeContentVisibility.PUBLIC);
        assertThat(loaded.slug()).isEqualTo("spring-data-jdbc-spike");
        assertThat(loaded.renderSnapshot()).isNotNull();
        assertThat(loaded.renderSnapshot().renderedHtml()).isEqualTo("<article><h1>Spike</h1></article>");
        assertThat(loaded.renderSnapshot().rendererVersion()).isEqualTo("v1");
        assertThat(loaded.renderSnapshot().readingTimeMinutes()).isEqualTo(4);
        assertThat(loaded.renderSnapshot().containsMermaid()).isTrue();
    }

    @Test
    void savesAndLoadsRenderedHeadingsInStableOrder() {
        SpikeContentItem published = publishedItem("heading-order")
                .withRenderSnapshot(snapshot("v1", List.of(
                        heading("details", 2, 1),
                        heading("intro", 1, 0),
                        heading("summary", 2, 2))));

        adapter.saveContentItem(published);

        SpikeContentItem loaded = adapter.findContentItem(published.id()).orElseThrow();
        assertThat(loaded.renderSnapshot().headings())
                .extracting(SpikeRenderedHeading::anchor)
                .containsExactly("intro", "details", "summary");
        assertThat(loaded.renderSnapshot().headings())
                .extracting(SpikeRenderedHeading::position)
                .containsExactly(0, 1, 2);
        assertThatThrownBy(() -> insertDuplicateHeadingAnchor(published.id()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void replacesRenderSnapshotAndHeadingsWithoutLeavingOldRows() {
        SpikeContentItem published = publishedItem("replace-render")
                .withRenderSnapshot(snapshot("v1", List.of(
                        heading("old-intro", 1, 0),
                        heading("old-details", 2, 1))));
        adapter.saveContentItem(published);

        SpikeContentItem loaded = adapter.findContentItem(published.id()).orElseThrow();
        SpikeContentItem replacement = loaded.withRenderSnapshot(snapshot("v2", List.of(
                heading("new-intro", 1, 0),
                heading("new-summary", 2, 1))));

        adapter.saveContentItem(replacement);

        SpikeContentItem reloaded = adapter.findContentItem(published.id()).orElseThrow();
        assertThat(reloaded.renderSnapshot().renderedHtml()).contains("Replacement");
        assertThat(reloaded.renderSnapshot().rendererVersion()).isEqualTo("v2");
        assertThat(reloaded.renderSnapshot().headings())
                .extracting(SpikeRenderedHeading::anchor)
                .containsExactly("new-intro", "new-summary");
        assertThat(countHeadings(published.id())).isEqualTo(2);
        assertThat(countHeadingAnchor(published.id(), "old-intro")).isZero();
        assertThat(countHeadingAnchor(published.id(), "old-details")).isZero();
    }

    @Test
    void usesOptimisticLockingForContentItemRoot() {
        SpikeContentItem saved = adapter.saveContentItem(publishedItem("optimistic-locking"));
        SpikeContentItem firstCopy = adapter.findContentItem(saved.id()).orElseThrow();
        SpikeContentItem staleCopy = adapter.findContentItem(saved.id()).orElseThrow();

        adapter.saveContentItem(firstCopy.withTitle("First update"));

        assertThatThrownBy(() -> adapter.saveContentItem(staleCopy.withTitle("Stale update")))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void enforcesRouteNamespaceUniqueness() {
        adapter.saveContentItem(publishedItem("unique-route"));

        assertThatThrownBy(() -> adapter.saveContentItem(publishedItem("unique-route")))
                .isInstanceOf(DataAccessException.class);
    }
}
