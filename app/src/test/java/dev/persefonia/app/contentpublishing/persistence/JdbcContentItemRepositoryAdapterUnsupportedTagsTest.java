package dev.persefonia.app.contentpublishing.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.content.TagId;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class JdbcContentItemRepositoryAdapterUnsupportedTagsTest extends ContentPublishingRepositoryTestDatabase {
    @Test
    void emptyTagIdsSaveAndLoad() {
        var saved = contentItems.save(ContentItemRepositoryTestFixtures.completeDraft("empty-tags"));

        assertThat(contentItems.findById(saved.id()).orElseThrow().tagIds()).isEmpty();
    }

    @Test
    void nonEmptyTagIdsFailExplicitly() {
        var item = ContentItemRepositoryTestFixtures.withTags(
                ContentItemRepositoryTestFixtures.completeDraft("non-empty-tags"),
                Set.of(TagId.newId()));

        assertThatThrownBy(() -> contentItems.save(item))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Content tag persistence is not available until taxonomy/content tagging is implemented.");
    }

    @Test
    void contentItemTagsTableDoesNotExist() {
        Boolean exists = namedJdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = 'publishing'
                      AND table_name = 'content_item_tags'
                )
                """,
                Map.of(),
                Boolean.class);

        assertThat(exists).isFalse();
    }
}
