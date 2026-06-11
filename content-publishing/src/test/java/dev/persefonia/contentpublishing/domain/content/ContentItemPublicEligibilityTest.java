package dev.persefonia.contentpublishing.domain.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import org.junit.jupiter.api.Test;

class ContentItemPublicEligibilityTest {
    @Test
    void draftVisibilityMatrixIsNeverPublic() {
        assertNeverPublic(ContentItemTestFixtures.draft(ContentVisibility.PUBLIC));
        assertNeverPublic(ContentItemTestFixtures.draft(ContentVisibility.UNLISTED));
        assertNeverPublic(ContentItemTestFixtures.draft(ContentVisibility.PRIVATE));
    }

    @Test
    void publishedVisibilityMatrixControlsPublicEligibility() {
        ContentItem publishedPublic = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        assertThat(publishedPublic.isPubliclyRenderable()).isTrue();
        assertThat(publishedPublic.isListedPublicly()).isTrue();
        assertThat(publishedPublic.isDirectUrlEligible()).isTrue();

        ContentItem publishedUnlisted = ContentItemTestFixtures.published(ContentVisibility.UNLISTED);
        assertThat(publishedUnlisted.isPubliclyRenderable()).isTrue();
        assertThat(publishedUnlisted.isListedPublicly()).isFalse();
        assertThat(publishedUnlisted.isDirectUrlEligible()).isTrue();

        assertNeverPublic(ContentItemTestFixtures.published(ContentVisibility.PRIVATE));
    }

    @Test
    void unpublishedVisibilityMatrixIsNeverPublic() {
        assertNeverPublic(unpublished(ContentVisibility.PUBLIC));
        assertNeverPublic(unpublished(ContentVisibility.UNLISTED));
        assertNeverPublic(unpublished(ContentVisibility.PRIVATE));
    }

    @Test
    void archivedVisibilityMatrixIsNeverPublic() {
        assertNeverPublic(archived(ContentVisibility.PUBLIC));
        assertNeverPublic(archived(ContentVisibility.UNLISTED));
        assertNeverPublic(archived(ContentVisibility.PRIVATE));
    }

    private static ContentItem unpublished(ContentVisibility visibility) {
        ContentItem item = ContentItemTestFixtures.published(visibility);
        item.unpublish(ContentItemTestFixtures.PUBLISHED_AT.plusSeconds(1));
        return item;
    }

    private static ContentItem archived(ContentVisibility visibility) {
        ContentItem item = ContentItemTestFixtures.published(visibility);
        item.archive(ContentItemTestFixtures.PUBLISHED_AT.plusSeconds(1));
        return item;
    }

    private static void assertNeverPublic(ContentItem item) {
        assertThat(item.isPubliclyRenderable()).isFalse();
        assertThat(item.isListedPublicly()).isFalse();
        assertThat(item.isDirectUrlEligible()).isFalse();
    }
}
