package dev.persefonia.contentpublishing.domain.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContentItemUnpublishLifecycleTest {
    private static final Instant UNPUBLISHED_AT = ContentItemTestFixtures.PUBLISHED_AT.plusSeconds(60);

    @Test
    void onlyPublishedContentCanBeUnpublished() {
        assertThatThrownBy(() -> ContentItemTestFixtures.draft().unpublish(UNPUBLISHED_AT))
                .isInstanceOf(ContentLifecycleException.class);
    }

    @Test
    void unpublishChangesLifecycleButPreservesPublishHistory() {
        ContentItem item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);

        item.unpublish(UNPUBLISHED_AT);

        assertThat(item.status()).isEqualTo(ContentStatus.UNPUBLISHED);
        assertThat(item.isUnpublished()).isTrue();
        assertThat(item.isDraft()).isFalse();
        assertThat(item.publishedAt()).contains(ContentItemTestFixtures.PUBLISHED_AT);
        assertThat(item.unpublishedAt()).contains(UNPUBLISHED_AT);
        assertThat(item.isPubliclyRenderable()).isFalse();
        assertThat(item.isListedPublicly()).isFalse();
        assertThat(item.isDirectUrlEligible()).isFalse();
    }
}
