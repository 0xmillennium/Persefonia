package dev.persefonia.contentpublishing.domain.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import org.junit.jupiter.api.Test;

class ContentItemDraftLifecycleTest {
    @Test
    void newDraftStartsIncompleteAndNonPublic() {
        ContentItem item = ContentItemTestFixtures.draft();

        assertThat(item.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(item.isDraft()).isTrue();
        assertThat(item.slug()).isEmpty();
        assertThat(item.title()).isEmpty();
        assertThat(item.summary()).isEmpty();
        assertThat(item.markdownSource()).isEmpty();
        assertThat(item.renderSnapshot()).isEmpty();
        assertThat(item.publishedAt()).isEmpty();
        assertThat(item.unpublishedAt()).isEmpty();
        assertThat(item.isPubliclyRenderable()).isFalse();
        assertThat(item.isListedPublicly()).isFalse();
        assertThat(item.isDirectUrlEligible()).isFalse();
    }
}
