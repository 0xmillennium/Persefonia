package dev.persefonia.app.contentpublishing.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.revision.ChangeNote;
import dev.persefonia.contentpublishing.domain.revision.CompleteContentSnapshot;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionMetadata;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.revision.RevisionType;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class JdbcContentRevisionRepositoryAdapterTest extends ContentPublishingRepositoryTestDatabase {
    @Test
    void savesAndFindsRevisionTypesById() {
        ContentId contentId = savedContentId("revision-types");
        var publish = contentRevisions.save(ContentRevisionRepositoryTestFixtures.revision(
                contentId, 1, RevisionType.PUBLISH, "revision-publish"));
        var manual = contentRevisions.save(ContentRevisionRepositoryTestFixtures.revision(
                contentId, 2, RevisionType.MANUAL_SNAPSHOT, "revision-manual"));
        var restore = contentRevisions.save(ContentRevisionRepositoryTestFixtures.revision(
                contentId, 3, RevisionType.RESTORE_SOURCE, "revision-restore"));

        assertRevision(contentRevisions.findById(publish.id()).orElseThrow(), RevisionType.PUBLISH, "revision-publish");
        assertRevision(contentRevisions.findById(manual.id()).orElseThrow(), RevisionType.MANUAL_SNAPSHOT, "revision-manual");
        assertRevision(contentRevisions.findById(restore.id()).orElseThrow(), RevisionType.RESTORE_SOURCE, "revision-restore");
        assertThat(contentRevisions.findById(ContentRevisionId.from(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void findsByContentIdOrderedByRevisionNumberAndLatestNumber() {
        ContentId contentId = savedContentId("revision-order");
        contentRevisions.save(ContentRevisionRepositoryTestFixtures.revision(
                contentId, 2, RevisionType.MANUAL_SNAPSHOT, "revision-two"));
        contentRevisions.save(ContentRevisionRepositoryTestFixtures.revision(
                contentId, 1, RevisionType.PUBLISH, "revision-one"));

        assertThat(contentRevisions.findByContentId(contentId))
                .extracting(revision -> revision.revisionNumber().value())
                .containsExactly(1, 2);
        assertThat(contentRevisions.findLatestRevisionNumber(contentId).orElseThrow().value()).isEqualTo(2);
        assertThat(contentRevisions.findLatestRevisionNumber(ContentId.newId())).isEmpty();
    }

    @Test
    void duplicateRevisionNumberAndIdFail() {
        ContentId contentId = savedContentId("revision-duplicates");
        var first = ContentRevisionRepositoryTestFixtures.revision(contentId, 1, RevisionType.PUBLISH, "revision-first");
        contentRevisions.save(first);

        assertThatThrownBy(() -> contentRevisions.save(ContentRevisionRepositoryTestFixtures.revision(
                contentId, 1, RevisionType.MANUAL_SNAPSHOT, "revision-second")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> contentRevisions.save(first))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void existingRevisionIsNotSilentlyUpdated() {
        ContentId contentId = savedContentId("revision-append-only");
        var original = ContentRevisionRepositoryTestFixtures.revision(contentId, 1, RevisionType.PUBLISH, "revision-original");
        contentRevisions.save(original);
        var attemptedUpdate = ContentRevision.create(
                original.id(),
                original.contentId(),
                original.revisionNumber(),
                original.revisionType(),
                CompleteContentSnapshot.of(
                        dev.persefonia.contentpublishing.domain.content.Title.of("Updated title"),
                        original.slug(),
                        original.summary(),
                        original.markdownSource(),
                        original.renderedHtml().orElse(null),
                        original.metadata()),
                original.createdBy(),
                original.createdAt(),
                ChangeNote.of("Attempted update"));

        assertThatThrownBy(() -> contentRevisions.save(attemptedUpdate))
                .isInstanceOf(DataAccessException.class);
        assertThat(contentRevisions.findById(original.id()).orElseThrow().title().value())
                .isEqualTo(original.title().value());
    }

    @Test
    void savingRevisionDoesNotMutateContentItem() {
        var savedItem = contentItems.save(ContentItemRepositoryTestFixtures.completeDraft("revision-no-mutation"));
        contentRevisions.save(ContentRevisionRepositoryTestFixtures.revision(
                savedItem.id(), 1, RevisionType.PUBLISH, "revision-no-mutation"));

        var reloadedItem = contentItems.findById(savedItem.id()).orElseThrow();
        assertThat(reloadedItem.version()).isEqualTo(savedItem.version());
        assertThat(reloadedItem.updatedAt()).isEqualTo(savedItem.updatedAt());
    }

    @Test
    void metadataCreatedByRenderedHtmlAndChangeNoteRoundTrip() {
        ContentId contentId = savedContentId("revision-metadata");
        var saved = contentRevisions.save(ContentRevisionRepositoryTestFixtures.revision(
                contentId, 1, RevisionType.PUBLISH, "revision-metadata"));

        var loaded = contentRevisions.findById(saved.id()).orElseThrow();
        assertThat(loaded.metadata().canonicalPath().orElseThrow().value()).isEqualTo("/articles/revision-metadata");
        assertThat(loaded.metadata().ogImageAssetId()).isPresent();
        assertThat(loaded.createdBy()).isEqualTo(ContentRevisionRepositoryTestFixtures.ADMIN);
        assertThat(loaded.renderedHtml().orElseThrow().value()).contains("revision-metadata");
        assertThat(loaded.changeNote().orElseThrow().value()).isEqualTo("Change 1");
    }

    private ContentId savedContentId(String slug) {
        return contentItems.save(ContentItemRepositoryTestFixtures.completeDraft(slug)).id();
    }

    private void assertRevision(ContentRevision revision, RevisionType type, String slug) {
        assertThat(revision.revisionType()).isEqualTo(type);
        assertThat(revision.slug().value()).isEqualTo(slug);
        assertThat(revision.createdBy()).isInstanceOf(AdminIdentityRef.class);
        assertThat(revision.metadata()).isInstanceOf(RevisionMetadata.class);
    }
}
