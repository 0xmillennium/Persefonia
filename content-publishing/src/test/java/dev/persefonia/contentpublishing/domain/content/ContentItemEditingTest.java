package dev.persefonia.contentpublishing.domain.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContentItemEditingTest {
    private static final Instant LATER = ContentItemTestFixtures.EDITED_AT.plusSeconds(30);

    @Test
    void changingTitleAndClearingTitleUpdatesUpdatedAt() {
        ContentItem item = ContentItemTestFixtures.draft();

        item.changeTitle(Title.of("Updated title"), ContentItemTestFixtures.EDITED_AT);
        assertThat(item.title()).contains(Title.of("Updated title"));
        assertThat(item.updatedAt()).isEqualTo(ContentItemTestFixtures.EDITED_AT);

        item.clearTitle(LATER);
        assertThat(item.title()).isEmpty();
        assertThat(item.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void changingSlugSummaryMarkdownMetadataAndVisibilityUpdatesUpdatedAt() {
        ContentItem item = ContentItemTestFixtures.draft();

        item.changeSlug(Slug.ofCanonical("updated-slug"), ContentItemTestFixtures.EDITED_AT);
        assertThat(item.updatedAt()).isEqualTo(ContentItemTestFixtures.EDITED_AT);

        item.changeSummary(Summary.of("Updated summary"), LATER);
        assertThat(item.updatedAt()).isEqualTo(LATER);

        item.changeMarkdownSource(MarkdownSource.of("Updated source"), LATER.plusSeconds(1));
        assertThat(item.updatedAt()).isEqualTo(LATER.plusSeconds(1));

        item.changeMetadata(ContentItemTestFixtures.metadataWithCanonicalPath(), LATER.plusSeconds(2));
        assertThat(item.updatedAt()).isEqualTo(LATER.plusSeconds(2));

        item.changeVisibility(ContentVisibility.PRIVATE, LATER.plusSeconds(3));
        assertThat(item.visibility()).isEqualTo(ContentVisibility.PRIVATE);
        assertThat(item.updatedAt()).isEqualTo(LATER.plusSeconds(3));
    }

    @Test
    void tagReplacementStoresTagIdsDefensively() {
        ContentItem item = ContentItemTestFixtures.draft();
        Set<TagId> tags = new HashSet<>();
        TagId tagId = TagId.newId();
        tags.add(tagId);

        item.replaceTags(tags, ContentItemTestFixtures.EDITED_AT);
        tags.clear();

        assertThat(item.tagIds()).containsExactly(tagId);
        assertThatThrownBy(() -> item.tagIds().add(TagId.newId()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void tagReplacementRejectsNullCollectionAndNullEntries() {
        ContentItem item = ContentItemTestFixtures.draft();

        assertThatThrownBy(() -> item.replaceTags(null, ContentItemTestFixtures.EDITED_AT))
                .isInstanceOf(NullPointerException.class);
        Set<TagId> tagsWithNull = new HashSet<>();
        tagsWithNull.add(null);
        assertThatThrownBy(() -> item.replaceTags(tagsWithNull, ContentItemTestFixtures.EDITED_AT))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void editingArchivedContentIsRejected() {
        ContentItem item = ContentItemTestFixtures.draft();
        item.archive(ContentItemTestFixtures.EDITED_AT);

        assertThatThrownBy(() -> item.changeTitle(Title.of("Nope"), LATER))
                .isInstanceOf(ContentLifecycleException.class);
    }
}
