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

class PublishingTranslationGroupsSchemaTest {
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
    void translationGroupTablesExist() throws SQLException {
        List<String> tables = PublishingSql.strings("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'publishing'
                  AND table_name IN ('translation_groups', 'translation_group_entries')
                """);
        assertThat(tables).containsExactlyInAnyOrder("translation_groups", "translation_group_entries");

        List<String> indexes = PublishingSql.strings("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'publishing' AND tablename = 'translation_group_entries'
                """);
        assertThat(indexes).contains(
                "ix_translation_group_entries_group_id",
                "ix_translation_group_entries_content_item_id",
                "ix_translation_group_entries_language");
    }

    @Test
    void contentItemsTableDoesNotOwnTranslationGroupForeignKey() throws SQLException {
        List<String> columns = PublishingSql.strings("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'publishing' AND table_name = 'content_items'
                """);
        assertThat(columns).doesNotContain("translation_group_id");
    }

    @Test
    void translationGroupEntryRequiresGroup() {
        UUID groupId = UUID.randomUUID();
        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), groupId, insertContentItem(), "TR", "ARTICLE"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void translationGroupEntryRequiresContentItem() throws SQLException {
        UUID groupId = insertGroup();
        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), groupId, UUID.randomUUID(), "TR", "ARTICLE"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void contentItemCanBelongToOnlyOneTranslationGroup() throws SQLException {
        UUID firstGroup = insertGroup();
        UUID secondGroup = insertGroup();
        UUID contentItem = insertContentItem();
        insertEntry(UUID.randomUUID(), firstGroup, contentItem, "TR", "ARTICLE");

        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), secondGroup, contentItem, "EN", "ARTICLE"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void groupCannotHaveDuplicateLanguage() throws SQLException {
        UUID groupId = insertGroup();
        insertEntry(UUID.randomUUID(), groupId, insertContentItem(), "TR", "ARTICLE");

        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), groupId, insertContentItem(), "TR", "ARTICLE"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void groupCannotHaveDuplicateContentItem() throws SQLException {
        UUID groupId = insertGroup();
        UUID contentItem = insertContentItem();
        insertEntry(UUID.randomUUID(), groupId, contentItem, "TR", "ARTICLE");

        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), groupId, contentItem, "EN", "ARTICLE"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void languageConstraintMatchesContentLanguages() throws SQLException {
        UUID groupId = insertGroup();
        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), groupId, insertContentItem(), "FR", "ARTICLE"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void contentTypeConstraintMatchesContentTypes() throws SQLException {
        UUID groupId = insertGroup();
        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), groupId, insertContentItem(), "TR", "BLOG"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void migrationTestsDoNotForbidFutureMigrations() throws SQLException {
        long applied = PublishingSql.count("""
                SELECT count(*)
                FROM operations.flyway_schema_history
                WHERE success = true
                """);
        assertThat(applied).isGreaterThanOrEqualTo(8);
    }

    private static UUID insertGroup() throws SQLException {
        UUID id = UUID.randomUUID();
        PublishingSql.update("""
                INSERT INTO publishing.translation_groups (id, created_at, updated_at, version)
                VALUES (?, ?, ?, ?)
                """, id, CREATED_AT, UPDATED_AT, 0L);
        return id;
    }

    private static UUID insertContentItem() throws SQLException {
        return ContentItemRows.insert(ContentItemRow.validDraft());
    }

    private static void insertEntry(UUID id, UUID groupId, UUID contentItemId, String language, String contentType)
            throws SQLException {
        PublishingSql.update("""
                INSERT INTO publishing.translation_group_entries (
                    id, translation_group_id, content_item_id, language, content_type, added_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, id, groupId, contentItemId, language, contentType, ADDED_AT);
    }
}
