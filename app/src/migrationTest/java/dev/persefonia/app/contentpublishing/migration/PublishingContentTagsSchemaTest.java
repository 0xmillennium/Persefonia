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

class PublishingContentTagsSchemaTest {
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
    void contentTagTableHasPublishingForeignKeyIndexAndNoTaxonomyForeignKey() throws SQLException {
        List<String> constraintsAndIndexes = PublishingSql.strings("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'publishing' AND table_name = 'content_item_tags'
                UNION
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'publishing' AND tablename = 'content_item_tags'
                """);
        List<String> foreignTargets = PublishingSql.strings("""
                SELECT ccu.table_schema
                FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name
                 AND ccu.constraint_schema = tc.constraint_schema
                WHERE tc.table_schema = 'publishing'
                  AND tc.table_name = 'content_item_tags'
                  AND tc.constraint_type = 'FOREIGN KEY'
                """);

        assertThat(constraintsAndIndexes).contains(
                "pk_content_item_tags",
                "fk_content_item_tags__content_items",
                "ix_content_item_tags_tag_id");
        assertThat(foreignTargets).containsExactly("publishing");
    }

    @Test
    void databaseAcceptsUnknownTagIdAndRejectsDuplicateAssignment() throws SQLException {
        UUID contentId = ContentItemRows.insert(ContentItemRow.validDraft());
        UUID unknownTagId = UUID.randomUUID();
        OffsetDateTime assignedAt = OffsetDateTime.parse("2026-06-15T10:00:00Z");

        PublishingSql.update("""
                INSERT INTO publishing.content_item_tags (content_item_id, tag_id, assigned_at)
                VALUES (?, ?, ?)
                """, contentId, unknownTagId, assignedAt);

        assertThat(PublishingSql.count("SELECT count(*) FROM publishing.content_item_tags")).isEqualTo(1);
        assertThatThrownBy(() -> PublishingSql.update("""
                INSERT INTO publishing.content_item_tags (content_item_id, tag_id, assigned_at)
                VALUES (?, ?, ?)
                """, contentId, unknownTagId, assignedAt))
                .isInstanceOf(SQLException.class);
    }
}
