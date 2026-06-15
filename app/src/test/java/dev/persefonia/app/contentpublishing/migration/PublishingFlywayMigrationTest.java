package dev.persefonia.app.contentpublishing.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PublishingFlywayMigrationTest {
    @BeforeAll
    static void migrateDatabase() {
        PublishingMigrationDatabase.start();
    }

    @AfterAll
    static void stopDatabase() {
        PublishingMigrationDatabase.stop();
    }

    @Test
    void cleanMigrateCreatesPublishingTables() throws SQLException {
        assertThatCode(() -> PublishingMigrationDatabase.cleanMigrate()).doesNotThrowAnyException();

        assertThat(existingPublishingTables()).containsExactlyInAnyOrder(
                "content_items",
                "content_render_snapshots",
                "content_rendered_headings",
                "content_revisions",
                "content_item_tags");
    }

    private static List<String> existingPublishingTables() throws SQLException {
        return PublishingSql.strings("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'publishing'
                  AND table_name IN (
                    'content_items',
                    'content_render_snapshots',
                    'content_rendered_headings',
                    'content_revisions',
                    'content_item_tags'
                  )
                """);
    }
}
