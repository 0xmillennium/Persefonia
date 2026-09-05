package dev.persefonia.app.contentpublishing.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublishingContentRevisionsSchemaTest {
    @BeforeAll
    static void migrateDatabase() {
        PublishingMigrationDatabase.start();
        PublishingMigrationDatabase.cleanMigrate();
    }

    @AfterAll
    static void stopDatabase() {
        PublishingMigrationDatabase.stop();
    }

    @BeforeEach
    void clearPublishingTables() throws SQLException {
        PublishingMigrationDatabase.truncatePublishing();
    }

    @Test
    void revisionRequiresExistingContentItem() {
        assertRevisionRejected(ContentRevisionRow.validPublish(UUID.randomUUID()));
    }

    @Test
    void revisionNumberNotPositiveIsRejected() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        assertRevisionRejected(ContentRevisionRow.validPublish(contentItemId).withRevisionNumber(0));
    }

    @Test
    void invalidRevisionTypeIsRejected() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        assertRevisionRejected(ContentRevisionRow.validPublish(contentItemId).withRevisionType("AUTO_SAVE"));
    }

    @Test
    void blankTitleIsRejected() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        assertRevisionRejected(ContentRevisionRow.validPublish(contentItemId).withTitle(" "));
    }

    @Test
    void invalidSlugIsRejected() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        assertRevisionRejected(ContentRevisionRow.validPublish(contentItemId).withSlug("bad_slug"));
    }

    @Test
    void blankSummaryIsRejected() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        assertRevisionRejected(ContentRevisionRow.validPublish(contentItemId).withSummary(" "));
    }

    @Test
    void blankMarkdownSourceIsRejected() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        assertRevisionRejected(ContentRevisionRow.validPublish(contentItemId).withMarkdownSource(" "));
    }

    @Test
    void blankRenderedHtmlIsRejectedWhenPresent() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        assertRevisionRejected(ContentRevisionRow.validPublish(contentItemId).withRenderedHtml(" "));
    }

    @Test
    void publishRevisionWithoutRenderedHtmlIsRejected() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        assertRevisionRejected(ContentRevisionRow.validPublish(contentItemId).withRenderedHtml(null));
    }

    @Test
    void manualSnapshotWithoutRenderedHtmlIsAccepted() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        ContentRevisionRows.insert(ContentRevisionRow.validPublish(contentItemId)
                .withRevisionType("MANUAL_SNAPSHOT")
                .withRenderedHtml(null));

        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void restoreSourceWithoutRenderedHtmlIsAccepted() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        ContentRevisionRows.insert(ContentRevisionRow.validPublish(contentItemId)
                .withRevisionType("RESTORE_SOURCE")
                .withRenderedHtml(null));

        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void duplicateContentItemIdAndRevisionNumberIsRejected() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();
        ContentRevisionRow revision = ContentRevisionRow.validPublish(contentItemId);
        ContentRevisionRows.insert(revision);

        assertRevisionRejected(revision.withNewId());
    }

    @Test
    void sameRevisionNumberIsAllowedForDifferentContentItemId() throws SQLException {
        UUID firstContentItemId = ContentItemRows.insertValidDraft();
        UUID secondContentItemId = ContentItemRows.insert(ContentItemRow.validDraft()
                .withId(UUID.randomUUID())
                .withSlug("second-slug"));
        ContentRevisionRows.insert(ContentRevisionRow.validPublish(firstContentItemId));

        ContentRevisionRows.insert(ContentRevisionRow.validPublish(firstContentItemId)
                .withNewId()
                .withContentItemId(secondContentItemId));

        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    void validPublishRevisionIsAccepted() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        ContentRevisionRows.insert(ContentRevisionRow.validPublish(contentItemId));

        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void validManualSnapshotRevisionIsAccepted() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        ContentRevisionRows.insert(ContentRevisionRow.validManualSnapshot(contentItemId));

        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void validRestoreSourceRevisionIsAccepted() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        ContentRevisionRows.insert(ContentRevisionRow.validRestoreSource(contentItemId));

        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void blankChangeNoteIsRejectedWhenPresent() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        assertRevisionRejected(ContentRevisionRow.validPublish(contentItemId).withChangeNote(" "));
    }

    @Test
    void changeNoteOverOneThousandCharactersIsRejected() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        assertRevisionRejected(ContentRevisionRow.validPublish(contentItemId).withChangeNote("a".repeat(1001)));
    }

    private static long rowCount() throws SQLException {
        return PublishingSql.count("SELECT count(*) FROM publishing.content_revisions");
    }

    private static void assertRevisionRejected(ContentRevisionRow row) {
        assertThatThrownBy(() -> ContentRevisionRows.insert(row))
                .isInstanceOf(SQLException.class);
    }
}
