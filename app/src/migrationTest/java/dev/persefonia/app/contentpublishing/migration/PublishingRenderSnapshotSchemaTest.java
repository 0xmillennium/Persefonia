package dev.persefonia.app.contentpublishing.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublishingRenderSnapshotSchemaTest {
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
    void renderSnapshotRequiresExistingContentItem() {
        assertRenderSnapshotRejected(RenderSnapshotRow.valid(UUID.randomUUID()));
    }

    @Test
    void renderSnapshotCascadesDeleteWhenContentItemIsDeleted() throws SQLException {
        UUID contentItemId = insertContentItemWithRenderSnapshot();

        PublishingSql.execute("DELETE FROM publishing.content_items WHERE id = '%s'::uuid".formatted(contentItemId));

        assertThat(rowCount("content_render_snapshots")).isZero();
    }

    @Test
    void blankRenderedHtmlIsRejected() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        assertRenderSnapshotRejected(RenderSnapshotRow.valid(contentItemId).withRenderedHtml(" "));
    }

    @Test
    void blankRendererVersionIsRejected() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        assertRenderSnapshotRejected(RenderSnapshotRow.valid(contentItemId).withRendererVersion(" "));
    }

    @Test
    void readingTimeMinutesLessThanOneIsRejected() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();

        assertRenderSnapshotRejected(RenderSnapshotRow.valid(contentItemId).withReadingTimeMinutes(0));
    }

    @Test
    void validRenderSnapshotIsAccepted() throws SQLException {
        insertContentItemWithRenderSnapshot();

        assertThat(rowCount("content_render_snapshots")).isEqualTo(1);
    }

    @Test
    void renderHeadingRequiresExistingRenderSnapshot() {
        assertRenderedHeadingRejected(RenderedHeadingRow.valid(UUID.randomUUID()));
    }

    @Test
    void headingLevelBelowOneIsRejected() throws SQLException {
        UUID contentItemId = insertContentItemWithRenderSnapshot();

        assertRenderedHeadingRejected(RenderedHeadingRow.valid(contentItemId).withLevel(0));
    }

    @Test
    void headingLevelAboveSixIsRejected() throws SQLException {
        UUID contentItemId = insertContentItemWithRenderSnapshot();

        assertRenderedHeadingRejected(RenderedHeadingRow.valid(contentItemId).withLevel(7));
    }

    @Test
    void blankHeadingTextIsRejected() throws SQLException {
        UUID contentItemId = insertContentItemWithRenderSnapshot();

        assertRenderedHeadingRejected(RenderedHeadingRow.valid(contentItemId).withText(" "));
    }

    @Test
    void blankHeadingAnchorIsRejected() throws SQLException {
        UUID contentItemId = insertContentItemWithRenderSnapshot();

        assertRenderedHeadingRejected(RenderedHeadingRow.valid(contentItemId).withAnchor(" "));
    }

    @Test
    void invalidHeadingAnchorIsRejected() throws SQLException {
        UUID contentItemId = insertContentItemWithRenderSnapshot();

        assertRenderedHeadingRejected(RenderedHeadingRow.valid(contentItemId).withAnchor("Bad_Anchor"));
    }

    @Test
    void headingPositionNotPositiveIsRejected() throws SQLException {
        UUID contentItemId = insertContentItemWithRenderSnapshot();

        assertRenderedHeadingRejected(RenderedHeadingRow.valid(contentItemId).withPosition(0));
    }

    @Test
    void duplicateHeadingAnchorForSameContentItemIsRejected() throws SQLException {
        UUID contentItemId = insertContentItemWithRenderSnapshot();
        RenderedHeadingRow heading = RenderedHeadingRow.valid(contentItemId);
        RenderedHeadingRows.insert(heading);

        assertRenderedHeadingRejected(heading.duplicateIdentity().withPosition(2));
    }

    @Test
    void duplicateHeadingPositionForSameContentItemIsRejected() throws SQLException {
        UUID contentItemId = insertContentItemWithRenderSnapshot();
        RenderedHeadingRow heading = RenderedHeadingRow.valid(contentItemId);
        RenderedHeadingRows.insert(heading);

        assertRenderedHeadingRejected(heading.duplicateIdentity().withAnchor("other-heading"));
    }

    @Test
    void headingsCascadeDeleteWhenRenderSnapshotIsDeleted() throws SQLException {
        UUID contentItemId = insertContentItemWithRenderSnapshot();
        RenderedHeadingRows.insert(RenderedHeadingRow.valid(contentItemId));

        PublishingSql.execute("DELETE FROM publishing.content_render_snapshots WHERE content_item_id = '%s'::uuid"
                .formatted(contentItemId));

        assertThat(rowCount("content_rendered_headings")).isZero();
    }

    private static UUID insertContentItemWithRenderSnapshot() throws SQLException {
        UUID contentItemId = ContentItemRows.insertValidDraft();
        RenderSnapshotRows.insert(RenderSnapshotRow.valid(contentItemId));
        return contentItemId;
    }

    private static long rowCount(String table) throws SQLException {
        return PublishingSql.count("SELECT count(*) FROM publishing.%s".formatted(table));
    }

    private static void assertRenderSnapshotRejected(RenderSnapshotRow row) {
        assertThatThrownBy(() -> RenderSnapshotRows.insert(row))
                .isInstanceOf(SQLException.class);
    }

    private static void assertRenderedHeadingRejected(RenderedHeadingRow row) {
        assertThatThrownBy(() -> RenderedHeadingRows.insert(row))
                .isInstanceOf(SQLException.class);
    }
}
