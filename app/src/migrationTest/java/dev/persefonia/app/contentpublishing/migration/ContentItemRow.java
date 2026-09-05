package dev.persefonia.app.contentpublishing.migration;

import java.time.OffsetDateTime;
import java.util.UUID;

record ContentItemRow(
        UUID id,
        String type,
        String status,
        String visibility,
        String language,
        String slug,
        String title,
        String summary,
        String markdownSource,
        String metaTitle,
        String metaDescription,
        String canonicalPath,
        String ogTitle,
        String ogDescription,
        UUID ogImageAssetId,
        OffsetDateTime publishedAt,
        OffsetDateTime unpublishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-06-11T08:00:00Z");
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-06-11T08:00:01Z");
    private static final OffsetDateTime PUBLISHED_AT = OffsetDateTime.parse("2026-06-11T09:00:00Z");
    private static final OffsetDateTime UNPUBLISHED_AT = OffsetDateTime.parse("2026-06-11T10:00:00Z");

    static ContentItemRow validDraft() {
        return new ContentItemRow(
                UUID.randomUUID(),
                "ARTICLE",
                "DRAFT",
                "PUBLIC",
                "TR",
                "valid-slug",
                "Valid title",
                "Valid summary",
                "Valid markdown source",
                null,
                null,
                "/articles/valid-slug",
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                CREATED_AT,
                UPDATED_AT,
                0);
    }

    ContentItemRow withId(UUID value) {
        return copy(value, type, status, visibility, language, slug, title, summary, markdownSource,
                canonicalPath, publishedAt, unpublishedAt, version);
    }

    ContentItemRow withType(String value) {
        return copy(id, value, status, visibility, language, slug, title, summary, markdownSource,
                canonicalPath, publishedAt, unpublishedAt, version);
    }

    ContentItemRow withStatus(String value) {
        return copy(id, type, value, visibility, language, slug, title, summary, markdownSource,
                canonicalPath, publishedAt, unpublishedAt, version);
    }

    ContentItemRow withVisibility(String value) {
        return copy(id, type, status, value, language, slug, title, summary, markdownSource,
                canonicalPath, publishedAt, unpublishedAt, version);
    }

    ContentItemRow withLanguage(String value) {
        return copy(id, type, status, visibility, value, slug, title, summary, markdownSource,
                canonicalPath, publishedAt, unpublishedAt, version);
    }

    ContentItemRow withSlug(String value) {
        return copy(id, type, status, visibility, language, value, title, summary, markdownSource,
                canonicalPath, publishedAt, unpublishedAt, version);
    }

    ContentItemRow withTitle(String value) {
        return copy(id, type, status, visibility, language, slug, value, summary, markdownSource,
                canonicalPath, publishedAt, unpublishedAt, version);
    }

    ContentItemRow withSummary(String value) {
        return copy(id, type, status, visibility, language, slug, title, value, markdownSource,
                canonicalPath, publishedAt, unpublishedAt, version);
    }

    ContentItemRow withMarkdownSource(String value) {
        return copy(id, type, status, visibility, language, slug, title, summary, value,
                canonicalPath, publishedAt, unpublishedAt, version);
    }

    ContentItemRow withCanonicalPath(String value) {
        return copy(id, type, status, visibility, language, slug, title, summary, markdownSource,
                value, publishedAt, unpublishedAt, version);
    }

    ContentItemRow withPublishedAt(OffsetDateTime value) {
        return copy(id, type, status, visibility, language, slug, title, summary, markdownSource,
                canonicalPath, value, unpublishedAt, version);
    }

    ContentItemRow withUnpublishedAt(OffsetDateTime value) {
        return copy(id, type, status, visibility, language, slug, title, summary, markdownSource,
                canonicalPath, publishedAt, value, version);
    }

    ContentItemRow withVersion(long value) {
        return copy(id, type, status, visibility, language, slug, title, summary, markdownSource,
                canonicalPath, publishedAt, unpublishedAt, value);
    }

    ContentItemRow incompleteDraft() {
        return copy(id, type, "DRAFT", visibility, language, null, null, null, null,
                null, null, null, version);
    }

    ContentItemRow published() {
        return withStatus("PUBLISHED").withPublishedAt(PUBLISHED_AT);
    }

    ContentItemRow unpublished() {
        return withStatus("UNPUBLISHED").withPublishedAt(PUBLISHED_AT).withUnpublishedAt(UNPUBLISHED_AT);
    }

    private ContentItemRow copy(
            UUID nextId,
            String nextType,
            String nextStatus,
            String nextVisibility,
            String nextLanguage,
            String nextSlug,
            String nextTitle,
            String nextSummary,
            String nextMarkdownSource,
            String nextCanonicalPath,
            OffsetDateTime nextPublishedAt,
            OffsetDateTime nextUnpublishedAt,
            long nextVersion) {
        return new ContentItemRow(
                nextId,
                nextType,
                nextStatus,
                nextVisibility,
                nextLanguage,
                nextSlug,
                nextTitle,
                nextSummary,
                nextMarkdownSource,
                metaTitle,
                metaDescription,
                nextCanonicalPath,
                ogTitle,
                ogDescription,
                ogImageAssetId,
                nextPublishedAt,
                nextUnpublishedAt,
                createdAt,
                updatedAt,
                nextVersion);
    }
}
