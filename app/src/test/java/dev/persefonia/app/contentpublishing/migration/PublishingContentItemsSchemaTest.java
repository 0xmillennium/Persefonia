package dev.persefonia.app.contentpublishing.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublishingContentItemsSchemaTest {
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
    void invalidContentStatusIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().withStatus("DELETED"));
    }

    @Test
    void invalidContentVisibilityIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().withVisibility("INTERNAL"));
    }

    @Test
    void invalidContentTypeIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().withType("BLOG"));
    }

    @Test
    void invalidContentLanguageIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().withLanguage("DE"));
    }

    @Test
    void invalidSlugIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().withSlug("bad_slug"));
    }

    @Test
    void blankSlugIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().withSlug(" "));
    }

    @Test
    void blankTitleIsRejectedWhenTitlePresent() {
        assertContentItemRejected(ContentItemRow.validDraft().withTitle(" "));
    }

    @Test
    void blankSummaryIsRejectedWhenSummaryPresent() {
        assertContentItemRejected(ContentItemRow.validDraft().withSummary(" "));
    }

    @Test
    void blankMarkdownSourceIsRejectedWhenMarkdownSourcePresent() {
        assertContentItemRejected(ContentItemRow.validDraft().withMarkdownSource(" "));
    }

    @Test
    void invalidCanonicalPathIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().withCanonicalPath("articles/has space"));
    }

    @Test
    void publishedWithoutSlugIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().published().withSlug(null));
    }

    @Test
    void publishedWithoutTitleIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().published().withTitle(null));
    }

    @Test
    void publishedWithoutSummaryIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().published().withSummary(null));
    }

    @Test
    void publishedWithoutMarkdownSourceIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().published().withMarkdownSource(null));
    }

    @Test
    void publishedWithoutCanonicalPathIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().published().withCanonicalPath(null));
    }

    @Test
    void publishedWithoutPublishedAtIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().published().withPublishedAt(null));
    }

    @Test
    void unpublishedWithoutPublishedAtIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().unpublished().withPublishedAt(null));
    }

    @Test
    void unpublishedWithoutUnpublishedAtIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().unpublished().withUnpublishedAt(null));
    }

    @Test
    void unpublishedAtBeforePublishedAtIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft()
                .unpublished()
                .withUnpublishedAt(OffsetDateTime.parse("2026-06-11T08:59:59Z")));
    }

    @Test
    void duplicateRouteNamespaceIsRejected() throws SQLException {
        ContentItemRows.insert(ContentItemRow.validDraft());

        assertContentItemRejected(ContentItemRow.validDraft().withId(UUID.randomUUID()));
    }

    @Test
    void sameSlugIsAllowedAcrossDifferentType() throws SQLException {
        ContentItemRows.insert(ContentItemRow.validDraft());

        ContentItemRows.insert(ContentItemRow.validDraft().withId(UUID.randomUUID()).withType("NOTE"));

        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    void sameSlugIsAllowedAcrossDifferentLanguage() throws SQLException {
        ContentItemRows.insert(ContentItemRow.validDraft());

        ContentItemRows.insert(ContentItemRow.validDraft().withId(UUID.randomUUID()).withLanguage("EN"));

        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    void draftWithNullableIncompleteFieldsIsAccepted() throws SQLException {
        ContentItemRows.insert(ContentItemRow.validDraft().incompleteDraft());

        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void unpublishedStatusPersistsCorrectly() throws SQLException {
        UUID id = ContentItemRows.insert(ContentItemRow.validDraft().unpublished());

        assertThat(PublishingSql.strings("""
                SELECT status
                FROM publishing.content_items
                WHERE id = '%s'::uuid
                """.formatted(id)))
                .containsExactly("UNPUBLISHED");
    }

    @Test
    void negativeVersionIsRejected() {
        assertContentItemRejected(ContentItemRow.validDraft().withVersion(-1));
    }

    private static long rowCount() throws SQLException {
        return PublishingSql.count("SELECT count(*) FROM publishing.content_items");
    }

    private static void assertContentItemRejected(ContentItemRow row) {
        assertThatThrownBy(() -> ContentItemRows.insert(row))
                .isInstanceOf(SQLException.class);
    }
}
