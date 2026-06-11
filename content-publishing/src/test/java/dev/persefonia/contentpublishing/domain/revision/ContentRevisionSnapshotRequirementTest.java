package dev.persefonia.contentpublishing.domain.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentValidationException;
import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import dev.persefonia.contentpublishing.domain.support.ContentRevisionTestFixtures;
import org.junit.jupiter.api.Test;

class ContentRevisionSnapshotRequirementTest {
    @Test
    void completeSnapshotRequiresCompleteSourceFieldsAndMetadata() {
        CompleteContentSnapshot valid = ContentRevisionTestFixtures.completeSnapshot();

        assertThatThrownBy(() -> CompleteContentSnapshot.of(null, valid.slug(), valid.summary(), valid.markdownSource(), null, valid.metadata()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CompleteContentSnapshot.of(valid.title(), null, valid.summary(), valid.markdownSource(), null, valid.metadata()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CompleteContentSnapshot.of(valid.title(), valid.slug(), null, valid.markdownSource(), null, valid.metadata()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CompleteContentSnapshot.of(valid.title(), valid.slug(), valid.summary(), null, null, valid.metadata()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CompleteContentSnapshot.of(valid.title(), valid.slug(), valid.summary(), valid.markdownSource(), null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void publishSnapshotPathRequiresRenderedHtml() {
        assertThatThrownBy(() -> ContentRevision.create(
                ContentRevisionId.newId(),
                ContentId.newId(),
                RevisionNumber.of(1),
                RevisionType.PUBLISH,
                ContentRevisionTestFixtures.sourceOnlySnapshot(),
                AdminIdentityRef.newId(),
                ContentRevisionTestFixtures.CREATED_AT,
                null))
                .isInstanceOf(ContentValidationException.class);
    }

    @Test
    void restoreSourceRevisionDoesNotMutateItemOrBypassPublishValidation() {
        ContentItem item = ContentItemTestFixtures.draft();

        ContentRevision revision = ContentRevision.restoreSourceSnapshot(
                ContentRevisionId.newId(),
                item.id(),
                RevisionNumber.of(2),
                ContentRevisionTestFixtures.sourceOnlySnapshot(),
                AdminIdentityRef.newId(),
                ContentRevisionTestFixtures.CREATED_AT,
                null);

        assertThat(revision.revisionType()).isEqualTo(RevisionType.RESTORE_SOURCE);
        assertThat(item.title()).isEmpty();
        assertThatThrownBy(() -> item.publish(ContentItemTestFixtures.renderSnapshot(), ContentItemTestFixtures.PUBLISHED_AT))
                .isInstanceOf(ContentValidationException.class);
    }
}
