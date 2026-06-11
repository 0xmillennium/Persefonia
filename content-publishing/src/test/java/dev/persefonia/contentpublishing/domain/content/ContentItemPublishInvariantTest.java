package dev.persefonia.contentpublishing.domain.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import org.junit.jupiter.api.Test;

class ContentItemPublishInvariantTest {
    @Test
    void cannotPublishWithoutRequiredFields() {
        assertThatThrownBy(() -> ContentItemTestFixtures.draft().publish(ContentItemTestFixtures.renderSnapshot(), ContentItemTestFixtures.PUBLISHED_AT))
                .isInstanceOf(ContentValidationException.class)
                .hasMessageContaining("slug");

        ContentItem withoutTitle = ContentItemTestFixtures.draft();
        withoutTitle.changeSlug(ContentItemTestFixtures.slug(), ContentItemTestFixtures.EDITED_AT);
        assertThatThrownBy(() -> withoutTitle.publish(ContentItemTestFixtures.renderSnapshot(), ContentItemTestFixtures.PUBLISHED_AT))
                .isInstanceOf(ContentValidationException.class)
                .hasMessageContaining("title");

        ContentItem withoutSummary = ContentItemTestFixtures.draft();
        withoutSummary.changeSlug(ContentItemTestFixtures.slug(), ContentItemTestFixtures.EDITED_AT);
        withoutSummary.changeTitle(ContentItemTestFixtures.title(), ContentItemTestFixtures.EDITED_AT);
        assertThatThrownBy(() -> withoutSummary.publish(ContentItemTestFixtures.renderSnapshot(), ContentItemTestFixtures.PUBLISHED_AT))
                .isInstanceOf(ContentValidationException.class)
                .hasMessageContaining("summary");

        ContentItem withoutMarkdown = ContentItemTestFixtures.draft();
        withoutMarkdown.changeSlug(ContentItemTestFixtures.slug(), ContentItemTestFixtures.EDITED_AT);
        withoutMarkdown.changeTitle(ContentItemTestFixtures.title(), ContentItemTestFixtures.EDITED_AT);
        withoutMarkdown.changeSummary(ContentItemTestFixtures.summary(), ContentItemTestFixtures.EDITED_AT);
        assertThatThrownBy(() -> withoutMarkdown.publish(ContentItemTestFixtures.renderSnapshot(), ContentItemTestFixtures.PUBLISHED_AT))
                .isInstanceOf(ContentValidationException.class)
                .hasMessageContaining("markdown source");
    }

    @Test
    void cannotPublishWithoutCanonicalPathOrRenderSnapshot() {
        ContentItem withoutCanonicalPath = ContentItemTestFixtures.completeDraft();
        withoutCanonicalPath.changeMetadata(ContentMetadata.empty(), ContentItemTestFixtures.EDITED_AT);

        assertThatThrownBy(() -> withoutCanonicalPath.publish(ContentItemTestFixtures.renderSnapshot(), ContentItemTestFixtures.PUBLISHED_AT))
                .isInstanceOf(ContentValidationException.class)
                .hasMessageContaining("canonical path");

        ContentItem withoutRenderSnapshot = ContentItemTestFixtures.completeDraft();
        assertThatThrownBy(() -> withoutRenderSnapshot.publish(null, ContentItemTestFixtures.PUBLISHED_AT))
                .isInstanceOf(ContentValidationException.class)
                .hasMessageContaining("render snapshot");
    }

    @Test
    void publishingCompleteContentSetsPublishedStateAndSnapshot() {
        ContentItem item = ContentItemTestFixtures.completeDraft();

        item.publish(ContentItemTestFixtures.renderSnapshot(), ContentItemTestFixtures.PUBLISHED_AT);

        assertThat(item.status()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(item.publishedAt()).contains(ContentItemTestFixtures.PUBLISHED_AT);
        assertThat(item.unpublishedAt()).isEmpty();
        assertThat(item.renderSnapshot()).isPresent();
        assertThat(item.renderSnapshot().orElseThrow().renderedHtml())
                .isEqualTo(ContentItemTestFixtures.renderSnapshot().renderedHtml());
        assertThat(item.isPubliclyRenderable()).isTrue();
    }

    @Test
    void republishPreservesPublishedAtAndClearsUnpublishedAt() {
        ContentItem item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        item.unpublish(ContentItemTestFixtures.PUBLISHED_AT.plusSeconds(60));

        item.publish(ContentItemTestFixtures.renderSnapshot(), ContentItemTestFixtures.PUBLISHED_AT.plusSeconds(120));

        assertThat(item.publishedAt()).contains(ContentItemTestFixtures.PUBLISHED_AT);
        assertThat(item.unpublishedAt()).isEmpty();
    }

    @Test
    void visibilityControlsPublicEligibilityAfterPublish() {
        ContentItem publicItem = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        ContentItem unlistedItem = ContentItemTestFixtures.published(ContentVisibility.UNLISTED);
        ContentItem privateItem = ContentItemTestFixtures.published(ContentVisibility.PRIVATE);

        assertThat(publicItem.isPubliclyRenderable()).isTrue();
        assertThat(publicItem.isListedPublicly()).isTrue();
        assertThat(publicItem.isDirectUrlEligible()).isTrue();
        assertThat(unlistedItem.isPubliclyRenderable()).isTrue();
        assertThat(unlistedItem.isListedPublicly()).isFalse();
        assertThat(unlistedItem.isDirectUrlEligible()).isTrue();
        assertThat(privateItem.isPubliclyRenderable()).isFalse();
        assertThat(privateItem.isListedPublicly()).isFalse();
        assertThat(privateItem.isDirectUrlEligible()).isFalse();
    }

    @Test
    void archivedContentCannotBePublished() {
        ContentItem item = ContentItemTestFixtures.completeDraft();
        item.archive(ContentItemTestFixtures.EDITED_AT);

        assertThatThrownBy(() -> item.publish(ContentItemTestFixtures.renderSnapshot(), ContentItemTestFixtures.PUBLISHED_AT))
                .isInstanceOf(ContentLifecycleException.class);
    }
}
