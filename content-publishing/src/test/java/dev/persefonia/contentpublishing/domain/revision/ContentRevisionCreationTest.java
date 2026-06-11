package dev.persefonia.contentpublishing.domain.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentValidationException;
import dev.persefonia.contentpublishing.domain.support.ContentRevisionTestFixtures;
import org.junit.jupiter.api.Test;

class ContentRevisionCreationTest {
    @Test
    void revisionRequiresCoreFields() {
        assertThatThrownBy(() -> revision(null, ContentId.newId(), RevisionNumber.of(1), RevisionType.PUBLISH,
                ContentRevisionTestFixtures.completeSnapshot(), AdminIdentityRef.newId()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> revision(ContentRevisionId.newId(), null, RevisionNumber.of(1), RevisionType.PUBLISH,
                ContentRevisionTestFixtures.completeSnapshot(), AdminIdentityRef.newId()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> revision(ContentRevisionId.newId(), ContentId.newId(), null, RevisionType.PUBLISH,
                ContentRevisionTestFixtures.completeSnapshot(), AdminIdentityRef.newId()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> revision(ContentRevisionId.newId(), ContentId.newId(), RevisionNumber.of(1), null,
                ContentRevisionTestFixtures.completeSnapshot(), AdminIdentityRef.newId()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> revision(ContentRevisionId.newId(), ContentId.newId(), RevisionNumber.of(1), RevisionType.PUBLISH,
                null, AdminIdentityRef.newId()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> revision(ContentRevisionId.newId(), ContentId.newId(), RevisionNumber.of(1), RevisionType.PUBLISH,
                ContentRevisionTestFixtures.completeSnapshot(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ContentRevision.create(
                ContentRevisionId.newId(),
                ContentId.newId(),
                RevisionNumber.of(1),
                RevisionType.PUBLISH,
                ContentRevisionTestFixtures.completeSnapshot(),
                AdminIdentityRef.newId(),
                null,
                null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void revisionStoresCompleteSnapshotAndOptionalChangeNote() {
        CompleteContentSnapshot snapshot = ContentRevisionTestFixtures.completeSnapshot();
        ChangeNote changeNote = ChangeNote.of("Publish snapshot");

        ContentRevision revision = ContentRevision.publishSnapshot(
                ContentRevisionId.newId(),
                ContentId.newId(),
                RevisionNumber.of(1),
                snapshot,
                AdminIdentityRef.newId(),
                ContentRevisionTestFixtures.CREATED_AT,
                changeNote);

        assertThat(revision.title()).isEqualTo(snapshot.title());
        assertThat(revision.slug()).isEqualTo(snapshot.slug());
        assertThat(revision.summary()).isEqualTo(snapshot.summary());
        assertThat(revision.markdownSource()).isEqualTo(snapshot.markdownSource());
        assertThat(revision.metadata()).isSameAs(snapshot.metadata());
        assertThat(revision.changeNote()).contains(changeNote);
        assertThat(revision.renderedHtml()).contains(ContentRevisionTestFixtures.renderedHtml());
    }

    @Test
    void publishRevisionRequiresRenderedHtml() {
        assertThatThrownBy(() -> ContentRevision.publishSnapshot(
                ContentRevisionId.newId(),
                ContentId.newId(),
                RevisionNumber.of(1),
                ContentRevisionTestFixtures.sourceOnlySnapshot(),
                AdminIdentityRef.newId(),
                ContentRevisionTestFixtures.CREATED_AT,
                null))
                .isInstanceOf(ContentValidationException.class);
    }

    private static ContentRevision revision(
            ContentRevisionId id,
            ContentId contentId,
            RevisionNumber revisionNumber,
            RevisionType revisionType,
            CompleteContentSnapshot snapshot,
            AdminIdentityRef createdBy) {
        return ContentRevision.create(
                id,
                contentId,
                revisionNumber,
                revisionType,
                snapshot,
                createdBy,
                ContentRevisionTestFixtures.CREATED_AT,
                null);
    }
}
