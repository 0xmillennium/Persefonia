package dev.persefonia.app.contentpublishing.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PublishingSchemaBoundaryTest {
    @BeforeAll
    static void migrateDatabase() {
        PublishingMigrationDatabase.start();
        PublishingMigrationDatabase.cleanMigrate();
    }

    @AfterAll
    static void stopDatabase() {
        PublishingMigrationDatabase.stop();
    }

    @Test
    void noPhysicalForeignKeyFromPublishingToOtherContexts() throws SQLException {
        assertThat(PublishingSchemaCatalog.foreignKeyCountFromPublishingTo("'iam', 'media', 'taxonomy', 'discovery'"))
                .isZero();
    }

    @Test
    void seriesFoundationPublishingTablesExist() throws SQLException {
        assertThat(PublishingSchemaCatalog.publishingTablesNamed("""
                    'series',
                    'series_entries'
                """))
                .containsExactlyInAnyOrder("series", "series_entries");
    }

    @Test
    void publishingSchemaDoesNotUseJsonbArraysOrSearchVectors() throws SQLException {
        assertThat(PublishingSchemaCatalog.publishingColumnCountMatching("udt_name IN ('json', 'jsonb')"))
                .isZero();

        assertThat(PublishingSchemaCatalog.publishingColumnCountMatching("data_type = 'ARRAY'"))
                .isZero();

        assertThat(PublishingSchemaCatalog.publishingColumnCountMatching("""
                    column_name = 'search_vector'
                    OR udt_name = 'tsvector'
                """))
                .isZero();
    }

    @Test
    void allRequiredIndexesExist() throws SQLException {
        assertThat(PublishingSchemaCatalog.publishingIndexNames())
                .contains(
                        "uq_content_items__route_namespace",
                        "ix_content_items__status",
                        "ix_content_items__visibility",
                        "ix_content_items__language",
                        "ix_content_items__type_status_visibility_language",
                        "ix_content_items__published_at",
                        "ix_content_items__updated_at",
                        "ix_content_items__route_lookup",
                        "ix_content_rendered_headings__content_position",
                        "ix_content_revisions__content_item_id",
                        "ix_content_revisions__content_revision_number",
                        "ix_content_revisions__created_at",
                        "ix_content_revisions__revision_type",
                        "ix_content_item_tags_tag_id",
                        "ix_translation_group_entries_group_id",
                        "ix_translation_group_entries_content_item_id",
                        "ix_translation_group_entries_language",
                        "ix_series_status",
                        "ix_series_updated_at",
                        "ix_series_entries_content_item_id");
    }

    @Test
    void allRequiredConstraintNamesExist() throws SQLException {
        assertThat(constraintAndIndexNames()).contains(
                "pk_content_items",
                "ck_content_items__type",
                "ck_content_items__status",
                "ck_content_items__visibility",
                "ck_content_items__language",
                "ck_content_items__slug_not_blank",
                "ck_content_items__slug_format",
                "ck_content_items__title_not_blank",
                "ck_content_items__summary_not_blank",
                "ck_content_items__markdown_source_not_blank",
                "ck_content_items__canonical_path_not_blank",
                "ck_content_items__canonical_path_format",
                "ck_content_items__published_complete",
                "ck_content_items__unpublished_requires_publish_timestamps",
                "ck_content_items__unpublished_requires_published_at",
                "ck_content_items__unpublished_not_before_published",
                "ck_content_items__updated_not_before_created",
                "ck_content_items__version_non_negative",
                "ck_content_items__title_length",
                "ck_content_items__summary_length",
                "ck_content_items__meta_title_length",
                "ck_content_items__meta_description_length",
                "ck_content_items__og_title_length",
                "ck_content_items__og_description_length",
                "ck_content_items__canonical_path_length",
                "uq_content_items__route_namespace",
                "pk_content_render_snapshots",
                "fk_content_render_snapshots__content_items",
                "ck_content_render_snapshots__rendered_html_not_blank",
                "ck_content_render_snapshots__renderer_version_not_blank",
                "ck_content_render_snapshots__reading_time_positive",
                "pk_content_rendered_headings",
                "fk_content_rendered_headings__render_snapshots",
                "uq_content_rendered_headings__content_anchor",
                "uq_content_rendered_headings__content_position",
                "ck_content_rendered_headings__level",
                "ck_content_rendered_headings__text_not_blank",
                "ck_content_rendered_headings__anchor_not_blank",
                "ck_content_rendered_headings__anchor_format",
                "ck_content_rendered_headings__position_positive",
                "pk_content_revisions",
                "fk_content_revisions__content_items",
                "uq_content_revisions__content_revision_number",
                "ck_content_revisions__revision_number_positive",
                "ck_content_revisions__revision_type",
                "ck_content_revisions__title_not_blank",
                "ck_content_revisions__slug_not_blank",
                "ck_content_revisions__slug_format",
                "ck_content_revisions__summary_not_blank",
                "ck_content_revisions__markdown_source_not_blank",
                "ck_content_revisions__rendered_html_not_blank",
                "ck_content_revisions__canonical_path_not_blank",
                "ck_content_revisions__canonical_path_format",
                "ck_content_revisions__change_note_not_blank",
                "ck_content_revisions__publish_has_rendered_html",
                "ck_content_revisions__title_length",
                "ck_content_revisions__summary_length",
                "ck_content_revisions__meta_title_length",
                "ck_content_revisions__meta_description_length",
                "ck_content_revisions__og_title_length",
                "ck_content_revisions__og_description_length",
                "ck_content_revisions__canonical_path_length",
                "ck_content_revisions__change_note_length",
                "pk_content_item_tags",
                "fk_content_item_tags__content_items",
                "pk_translation_groups",
                "ck_translation_groups__updated_not_before_created",
                "ck_translation_groups__version_non_negative",
                "pk_translation_group_entries",
                "fk_translation_group_entries__translation_groups",
                "fk_translation_group_entries__content_items",
                "uq_translation_group_entries__content_item",
                "uq_translation_group_entries__group_language",
                "ck_translation_group_entries__language",
                "ck_translation_group_entries__content_type",
                "pk_series",
                "uq_series__language_slug",
                "ck_series__language",
                "ck_series__status",
                "ck_series__title_not_blank",
                "ck_series__slug_not_blank",
                "ck_series__slug_format",
                "ck_series__description_not_blank",
                "ck_series__updated_not_before_created",
                "ck_series__version_non_negative",
                "ck_series__title_length",
                "ck_series__description_length",
                "pk_series_entries",
                "fk_series_entries__series",
                "fk_series_entries__content_items",
                "uq_series_entries__series_content",
                "uq_series_entries__series_position",
                "ck_series_entries__position_positive");
    }

    private static List<String> constraintAndIndexNames() throws SQLException {
        return PublishingSchemaCatalog.publishingConstraintAndIndexNames();
    }
}
