package dev.persefonia.app.contentpublishing.migration;

import java.time.OffsetDateTime;
import java.util.UUID;

record ContentRevisionRow(
        UUID id,
        UUID contentItemId,
        int revisionNumber,
        String revisionType,
        String title,
        String slug,
        String summary,
        String markdownSource,
        String renderedHtml,
        String metaTitle,
        String metaDescription,
        String canonicalPath,
        String ogTitle,
        String ogDescription,
        UUID ogImageAssetId,
        UUID createdByAdminRef,
        OffsetDateTime createdAt,
        String changeNote) {
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-06-11T09:00:00Z");

    static ContentRevisionRow validPublish(UUID contentItemId) {
        return new ContentRevisionRow(
                UUID.randomUUID(),
                contentItemId,
                1,
                "PUBLISH",
                "Revision title",
                "revision-slug",
                "Revision summary",
                "Revision markdown source",
                "<p>Rendered</p>",
                null,
                null,
                "/articles/revision-slug",
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                CREATED_AT,
                null);
    }

    static ContentRevisionRow validManualSnapshot(UUID contentItemId) {
        return validPublish(contentItemId).withRevisionType("MANUAL_SNAPSHOT");
    }

    static ContentRevisionRow validRestoreSource(UUID contentItemId) {
        return validPublish(contentItemId).withRevisionType("RESTORE_SOURCE");
    }

    ContentRevisionRow withContentItemId(UUID value) {
        return copy(value, revisionNumber, revisionType, title, slug, summary, markdownSource, renderedHtml,
                changeNote);
    }

    ContentRevisionRow withRevisionNumber(int value) {
        return copy(contentItemId, value, revisionType, title, slug, summary, markdownSource, renderedHtml,
                changeNote);
    }

    ContentRevisionRow withRevisionType(String value) {
        return copy(contentItemId, revisionNumber, value, title, slug, summary, markdownSource, renderedHtml,
                changeNote);
    }

    ContentRevisionRow withTitle(String value) {
        return copy(contentItemId, revisionNumber, revisionType, value, slug, summary, markdownSource,
                renderedHtml, changeNote);
    }

    ContentRevisionRow withSlug(String value) {
        return copy(contentItemId, revisionNumber, revisionType, title, value, summary, markdownSource,
                renderedHtml, changeNote);
    }

    ContentRevisionRow withSummary(String value) {
        return copy(contentItemId, revisionNumber, revisionType, title, slug, value, markdownSource,
                renderedHtml, changeNote);
    }

    ContentRevisionRow withMarkdownSource(String value) {
        return copy(contentItemId, revisionNumber, revisionType, title, slug, summary, value, renderedHtml,
                changeNote);
    }

    ContentRevisionRow withRenderedHtml(String value) {
        return copy(contentItemId, revisionNumber, revisionType, title, slug, summary, markdownSource, value,
                changeNote);
    }

    ContentRevisionRow withChangeNote(String value) {
        return copy(contentItemId, revisionNumber, revisionType, title, slug, summary, markdownSource,
                renderedHtml, value);
    }

    ContentRevisionRow withNewId() {
        return new ContentRevisionRow(
                UUID.randomUUID(),
                contentItemId,
                revisionNumber,
                revisionType,
                title,
                slug,
                summary,
                markdownSource,
                renderedHtml,
                metaTitle,
                metaDescription,
                canonicalPath,
                ogTitle,
                ogDescription,
                ogImageAssetId,
                createdByAdminRef,
                createdAt,
                changeNote);
    }

    private ContentRevisionRow copy(
            UUID nextContentItemId,
            int nextRevisionNumber,
            String nextRevisionType,
            String nextTitle,
            String nextSlug,
            String nextSummary,
            String nextMarkdownSource,
            String nextRenderedHtml,
            String nextChangeNote) {
        return new ContentRevisionRow(
                id,
                nextContentItemId,
                nextRevisionNumber,
                nextRevisionType,
                nextTitle,
                nextSlug,
                nextSummary,
                nextMarkdownSource,
                nextRenderedHtml,
                metaTitle,
                metaDescription,
                canonicalPath,
                ogTitle,
                ogDescription,
                ogImageAssetId,
                createdByAdminRef,
                createdAt,
                nextChangeNote);
    }
}
