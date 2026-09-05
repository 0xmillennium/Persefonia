package dev.persefonia.app.contentpublishing.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublishingSeriesSchemaTest {
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-06-15T08:00:00Z");
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-06-15T08:00:01Z");
    private static final OffsetDateTime ADDED_AT = OffsetDateTime.parse("2026-06-15T08:00:02Z");

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
    void seriesTablesExist() throws SQLException {
        List<String> tables = PublishingSchemaCatalog.publishingTablesNamed("'series', 'series_entries'");
        assertThat(tables).containsExactlyInAnyOrder("series", "series_entries");

        assertThat(PublishingSchemaCatalog.publishingIndexNames())
                .contains("ix_series_status", "ix_series_updated_at", "ix_series_entries_content_item_id")
                .doesNotContain("ix_series_language", "ix_series_entries_series_id");
    }

    @Test
    void seriesRequiresLanguage() {
        assertThatThrownBy(() -> insertSeries(UUID.randomUUID(), null, "path", "Path", "ACTIVE"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void seriesRequiresTitle() {
        assertThatThrownBy(() -> insertSeries(UUID.randomUUID(), "EN", "path", " ", "ACTIVE"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void seriesRequiresSlug() {
        assertThatThrownBy(() -> insertSeries(UUID.randomUUID(), "EN", "Bad Slug", "Path", "ACTIVE"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void seriesSlugUniquePerLanguage() throws SQLException {
        insertSeries(UUID.randomUUID(), "EN", "path", "First", "ACTIVE");

        assertThatThrownBy(() -> insertSeries(UUID.randomUUID(), "EN", "path", "Second", "ACTIVE"))
                .isInstanceOf(SQLException.class);

        insertSeries(UUID.randomUUID(), "TR", "path", "Second", "ACTIVE");
    }

    @Test
    void seriesEntryRequiresSeries() {
        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), UUID.randomUUID(), insertContentItem(), 1))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void seriesEntryRequiresContentItem() throws SQLException {
        UUID series = insertSeries(UUID.randomUUID(), "EN", "path", "Path", "ACTIVE");

        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), series, UUID.randomUUID(), 1))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void seriesEntryPositionMustBePositive() throws SQLException {
        UUID series = insertSeries(UUID.randomUUID(), "EN", "path", "Path", "ACTIVE");

        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), series, insertContentItem(), 0))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void seriesEntryContentUniqueWithinSeries() throws SQLException {
        UUID series = insertSeries(UUID.randomUUID(), "EN", "path", "Path", "ACTIVE");
        UUID contentItem = insertContentItem();
        insertEntry(UUID.randomUUID(), series, contentItem, 1);

        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), series, contentItem, 2))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void seriesEntryPositionUniqueWithinSeries() throws SQLException {
        UUID series = insertSeries(UUID.randomUUID(), "EN", "path", "Path", "ACTIVE");
        insertEntry(UUID.randomUUID(), series, insertContentItem(), 1);

        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), series, insertContentItem(), 1))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void contentItemMayAppearInMultipleSeries() throws SQLException {
        UUID first = insertSeries(UUID.randomUUID(), "EN", "first", "First", "ACTIVE");
        UUID second = insertSeries(UUID.randomUUID(), "EN", "second", "Second", "ACTIVE");
        UUID contentItem = insertContentItem();

        insertEntry(UUID.randomUUID(), first, contentItem, 1);
        insertEntry(UUID.randomUUID(), second, contentItem, 1);
    }

    @Test
    void contentItemHasNoSeriesIdColumn() throws SQLException {
        List<String> columns = PublishingSql.strings("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'publishing' AND table_name = 'content_items'
                """);
        assertThat(columns).doesNotContain("series_id");
    }

    @Test
    void migrationTestsDoNotForbidFutureMigrations() throws SQLException {
        long applied = PublishingSql.count("""
                SELECT count(*)
                FROM operations.flyway_schema_history
                WHERE success = true
                """);
        assertThat(applied).isGreaterThanOrEqualTo(9);
    }

    private static UUID insertSeries(UUID id, String language, String slug, String title, String status)
            throws SQLException {
        PublishingSql.update("""
                INSERT INTO publishing.series (
                    id, language, slug, title, description, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, language, slug, title, null, status, CREATED_AT, UPDATED_AT, 0L);
        return id;
    }

    private static UUID insertContentItem() throws SQLException {
        UUID id = UUID.randomUUID();
        return ContentItemRows.insert(ContentItemRow.validDraft().withId(id).withSlug("content-" + id.toString().substring(0, 8)));
    }

    private static void insertEntry(UUID id, UUID seriesId, UUID contentItemId, int position) throws SQLException {
        PublishingSql.update("""
                INSERT INTO publishing.series_entries (id, series_id, content_item_id, position, added_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, seriesId, contentItemId, position, ADDED_AT);
    }
}
