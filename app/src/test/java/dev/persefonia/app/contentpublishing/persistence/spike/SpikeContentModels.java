package dev.persefonia.app.contentpublishing.persistence.spike;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

enum SpikeContentType {
    ARTICLE,
    NOTE,
    RESEARCH,
    PAGE
}

enum SpikeContentStatus {
    DRAFT,
    PUBLISHED,
    UNPUBLISHED,
    ARCHIVED
}

enum SpikeContentVisibility {
    PUBLIC,
    UNLISTED,
    PRIVATE
}

enum SpikeLanguage {
    TR,
    EN
}

enum SpikeRevisionType {
    AUTHOR_EDIT,
    SYSTEM_SNAPSHOT
}

record SpikeContentItem(
        UUID id,
        SpikeContentType type,
        SpikeContentStatus status,
        SpikeContentVisibility visibility,
        SpikeLanguage language,
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
        Instant publishedAt,
        Instant unpublishedAt,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        SpikeContentRenderSnapshot renderSnapshot) {
    SpikeContentItem withRenderSnapshot(SpikeContentRenderSnapshot replacement) {
        return new SpikeContentItem(
                id,
                type,
                status,
                visibility,
                language,
                slug,
                title,
                summary,
                markdownSource,
                metaTitle,
                metaDescription,
                canonicalPath,
                ogTitle,
                ogDescription,
                ogImageAssetId,
                publishedAt,
                unpublishedAt,
                createdAt,
                updatedAt,
                version,
                replacement);
    }

    SpikeContentItem withTitle(String replacement) {
        return new SpikeContentItem(
                id,
                type,
                status,
                visibility,
                language,
                slug,
                replacement,
                summary,
                markdownSource,
                metaTitle,
                metaDescription,
                canonicalPath,
                ogTitle,
                ogDescription,
                ogImageAssetId,
                publishedAt,
                unpublishedAt,
                createdAt,
                updatedAt.plusSeconds(1),
                version,
                renderSnapshot);
    }

    SpikeContentItem withStatus(SpikeContentStatus replacement) {
        return new SpikeContentItem(
                id,
                type,
                replacement,
                visibility,
                language,
                slug,
                title,
                summary,
                markdownSource,
                metaTitle,
                metaDescription,
                canonicalPath,
                ogTitle,
                ogDescription,
                ogImageAssetId,
                publishedAt,
                unpublishedAt,
                createdAt,
                updatedAt,
                version,
                renderSnapshot);
    }

    SpikeContentItem withVisibility(SpikeContentVisibility replacement) {
        return new SpikeContentItem(
                id,
                type,
                status,
                replacement,
                language,
                slug,
                title,
                summary,
                markdownSource,
                metaTitle,
                metaDescription,
                canonicalPath,
                ogTitle,
                ogDescription,
                ogImageAssetId,
                publishedAt,
                unpublishedAt,
                createdAt,
                updatedAt,
                version,
                renderSnapshot);
    }
}

record SpikeContentRenderSnapshot(
        String renderedHtml,
        Instant renderedAt,
        String rendererVersion,
        int readingTimeMinutes,
        boolean containsMermaid,
        List<SpikeRenderedHeading> headings) {
}

record SpikeRenderedHeading(
        UUID id,
        int level,
        String text,
        String anchor,
        int position) {
}

record SpikeContentRevision(
        UUID id,
        UUID contentItemId,
        int revisionNumber,
        SpikeRevisionType revisionType,
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
        Instant createdAt,
        String changeNote) {
    SpikeContentRevision withRenderedHtml(String replacement) {
        return new SpikeContentRevision(
                id,
                contentItemId,
                revisionNumber,
                revisionType,
                title,
                slug,
                summary,
                markdownSource,
                replacement,
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

    SpikeContentRevision withOgImageAssetId(UUID replacement) {
        return new SpikeContentRevision(
                id,
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
                replacement,
                createdByAdminRef,
                createdAt,
                changeNote);
    }

    SpikeContentRevision withTitle(String replacement) {
        return new SpikeContentRevision(
                id,
                contentItemId,
                revisionNumber,
                revisionType,
                replacement,
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

    SpikeContentRevision withChangeNote(String replacement) {
        return new SpikeContentRevision(
                id,
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
                replacement);
    }
}
