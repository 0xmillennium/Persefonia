package dev.persefonia.app.contentpublishing.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.TagId;
import dev.persefonia.contentpublishing.domain.content.Title;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

class JdbcContentItemRepositoryAdapterTagsTest extends ContentPublishingRepositoryTestDatabase {
    @Test
    void savesAndLoadsNonEmptyTagIdsAsAggregateState() {
        TagId first = TagId.newId();
        TagId second = TagId.newId();
        var item = ContentItemRepositoryTestFixtures.withTags(
                ContentItemRepositoryTestFixtures.completeDraft("tag-round-trip"),
                Set.of(first, second));

        var saved = contentItems.save(item);

        assertThat(saved.tagIds()).containsExactlyInAnyOrder(first, second);
        assertThat(contentItems.findById(saved.id()).orElseThrow().tagIds())
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void synchronizesDifferentiallyAndUsesAggregateMutationTimeForNewRelations() {
        TagId retained = TagId.newId();
        TagId removed = TagId.newId();
        TagId added = TagId.newId();
        var saved = contentItems.save(ContentItemRepositoryTestFixtures.withTags(
                ContentItemRepositoryTestFixtures.completeDraft("tag-diff"),
                Set.of(retained, removed)));
        Instant retainedAssignedAt = assignedAt(saved.id().value(), retained);
        Instant mutationTime = ContentItemRepositoryTestFixtures.NOW.plusSeconds(70);

        saved.replaceTags(Set.of(retained, added), mutationTime);
        var updated = contentItems.save(saved);

        assertThat(updated.tagIds()).containsExactlyInAnyOrder(retained, added);
        assertThat(assignedAt(saved.id().value(), retained)).isEqualTo(retainedAssignedAt);
        assertThat(assignedAt(saved.id().value(), added)).isEqualTo(mutationTime);
        assertThat(relationExists(saved.id().value(), removed)).isFalse();
    }

    @Test
    void unrelatedRootSaveDoesNotRewriteAssignmentTimestampAndEmptySetRemovesAllRows() {
        TagId tagId = TagId.newId();
        var saved = contentItems.save(ContentItemRepositoryTestFixtures.withTags(
                ContentItemRepositoryTestFixtures.completeDraft("tag-retained"),
                Set.of(tagId)));
        Instant originalAssignedAt = assignedAt(saved.id().value(), tagId);

        saved.changeTitle(Title.of("Unrelated update"), ContentItemRepositoryTestFixtures.NOW.plusSeconds(80));
        var updated = contentItems.save(saved);
        assertThat(assignedAt(saved.id().value(), tagId)).isEqualTo(originalAssignedAt);

        updated.replaceTags(Set.of(), ContentItemRepositoryTestFixtures.NOW.plusSeconds(90));
        contentItems.save(updated);
        assertThat(relationCount(saved.id().value())).isZero();
    }

    @Test
    void alternateLoadPathsAndAssignedTagQueryHydrateCompleteRoots() {
        TagId draftTag = TagId.newId();
        var draft = contentItems.save(ContentItemRepositoryTestFixtures.withTags(
                ContentItemRepositoryTestFixtures.completeDraft("tag-draft"),
                Set.of(draftTag)));
        TagId publishedTag = TagId.newId();
        var published = contentItems.save(ContentItemRepositoryTestFixtures.withTags(
                ContentItemRepositoryTestFixtures.published("tag-route", ContentVisibility.PUBLIC),
                Set.of(publishedTag)));

        assertThat(contentItems.findDrafts()).singleElement().satisfies(item ->
                assertThat(item.tagIds()).containsExactly(draftTag));
        assertThat(contentItems.findByStatus(ContentStatus.PUBLISHED)).singleElement().satisfies(item ->
                assertThat(item.tagIds()).containsExactly(publishedTag));
        assertThat(contentItems.findBySlugAndTypeAndLanguage(
                Slug.ofCanonical("tag-route"), ContentType.ARTICLE, ContentLanguage.EN))
                .get().extracting(item -> item.tagIds()).isEqualTo(Set.of(publishedTag));
        assertThat(contentItems.findPublishedByRoute(
                ContentType.ARTICLE, Slug.ofCanonical("tag-route"), ContentLanguage.EN))
                .get().extracting(item -> item.tagIds()).isEqualTo(Set.of(publishedTag));
        assertThat(contentItems.findByAssignedTagId(draftTag))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(draft.id());
                    assertThat(item.tagIds()).containsExactly(draftTag);
                });
        assertThat(published.id()).isNotEqualTo(draft.id());
    }

    @Test
    void staleTagSaveFailsBeforeRelationshipSynchronization() {
        TagId original = TagId.newId();
        TagId winning = TagId.newId();
        TagId stale = TagId.newId();
        var saved = contentItems.save(ContentItemRepositoryTestFixtures.withTags(
                ContentItemRepositoryTestFixtures.completeDraft("tag-lock"),
                Set.of(original)));
        var firstCopy = contentItems.findById(saved.id()).orElseThrow();
        var staleCopy = contentItems.findById(saved.id()).orElseThrow();

        firstCopy.replaceTags(Set.of(winning), ContentItemRepositoryTestFixtures.NOW.plusSeconds(100));
        contentItems.save(firstCopy);
        staleCopy.replaceTags(Set.of(stale), ContentItemRepositoryTestFixtures.NOW.plusSeconds(101));

        assertThatThrownBy(() -> contentItems.save(staleCopy))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(contentItems.findById(saved.id()).orElseThrow().tagIds()).containsExactly(winning);
        assertThat(relationExists(saved.id().value(), stale)).isFalse();
    }

    @Test
    void childSynchronizationFailureRollsBackRootAndRelationshipChanges() {
        TagId original = TagId.newId();
        TagId rejected = TagId.newId();
        var saved = contentItems.save(ContentItemRepositoryTestFixtures.withTags(
                ContentItemRepositoryTestFixtures.completeDraft("tag-transaction"),
                Set.of(original)));
        String constraintName = "ck_content_item_tags_test_rejected";
        jdbc.execute("ALTER TABLE publishing.content_item_tags ADD CONSTRAINT " + constraintName
                + " CHECK (tag_id <> '" + rejected.value() + "'::uuid)");
        try {
            saved.changeTitle(Title.of("Must roll back"), ContentItemRepositoryTestFixtures.NOW.plusSeconds(110));
            saved.replaceTags(Set.of(rejected), ContentItemRepositoryTestFixtures.NOW.plusSeconds(111));

            assertThatThrownBy(() -> contentItems.save(saved))
                    .isInstanceOf(DataIntegrityViolationException.class);

            var reloaded = contentItems.findById(saved.id()).orElseThrow();
            assertThat(reloaded.title().orElseThrow().value()).isEqualTo("Title tag-transaction");
            assertThat(reloaded.tagIds()).containsExactly(original);
        } finally {
            jdbc.execute("ALTER TABLE publishing.content_item_tags DROP CONSTRAINT " + constraintName);
        }
    }

    private Instant assignedAt(java.util.UUID contentItemId, TagId tagId) {
        Timestamp value = namedJdbc.queryForObject("""
                SELECT assigned_at
                FROM publishing.content_item_tags
                WHERE content_item_id = :contentItemId
                  AND tag_id = :tagId
                """, Map.of("contentItemId", contentItemId, "tagId", tagId.value()), Timestamp.class);
        return value.toInstant();
    }

    private boolean relationExists(java.util.UUID contentItemId, TagId tagId) {
        Boolean exists = namedJdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM publishing.content_item_tags
                    WHERE content_item_id = :contentItemId
                      AND tag_id = :tagId
                )
                """, Map.of("contentItemId", contentItemId, "tagId", tagId.value()), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private int relationCount(java.util.UUID contentItemId) {
        Integer count = namedJdbc.queryForObject("""
                SELECT COUNT(*)
                FROM publishing.content_item_tags
                WHERE content_item_id = :contentItemId
                """, Map.of("contentItemId", contentItemId), Integer.class);
        return count == null ? 0 : count;
    }
}
