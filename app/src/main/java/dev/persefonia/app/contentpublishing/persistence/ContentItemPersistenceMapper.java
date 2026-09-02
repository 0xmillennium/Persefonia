package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.domain.content.AssetId;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.HeadingAnchor;
import dev.persefonia.contentpublishing.domain.content.HeadingLevel;
import dev.persefonia.contentpublishing.domain.content.HeadingText;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.OpenGraphDescription;
import dev.persefonia.contentpublishing.domain.content.OpenGraphTitle;
import dev.persefonia.contentpublishing.domain.content.ReadingTime;
import dev.persefonia.contentpublishing.domain.content.RenderedHeading;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.content.RendererVersion;
import dev.persefonia.contentpublishing.domain.content.SeoDescription;
import dev.persefonia.contentpublishing.domain.content.SeoTitle;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.SortOrder;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.TagId;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.content.Version;
import java.util.List;
import java.util.Set;

final class ContentItemPersistenceMapper {
    ContentItemPersistenceEntity toEntity(ContentItem item, boolean newRow) {
        return toEntity(item, newRow ? null : previousJdbcVersion(item.version()));
    }

    ContentItemPersistenceEntity toEntity(ContentItem item, Long jdbcVersion) {
        return new ContentItemPersistenceEntity(
                item.id().value(),
                item.type().name(),
                item.status().name(),
                item.visibility().name(),
                item.language().name(),
                item.slug().map(slug -> slug.value()).orElse(null),
                item.title().map(title -> title.value()).orElse(null),
                item.summary().map(summary -> summary.value()).orElse(null),
                item.markdownSource().map(source -> source.value()).orElse(null),
                item.metadata().seoTitle().map(seoTitle -> seoTitle.value()).orElse(null),
                item.metadata().seoDescription().map(seoDescription -> seoDescription.value()).orElse(null),
                item.metadata().canonicalPath().map(canonicalPath -> canonicalPath.value()).orElse(null),
                item.metadata().openGraphTitle().map(openGraphTitle -> openGraphTitle.value()).orElse(null),
                item.metadata().openGraphDescription().map(openGraphDescription -> openGraphDescription.value()).orElse(null),
                item.metadata().ogImageAssetId().map(assetId -> assetId.value()).orElse(null),
                item.publishedAt().orElse(null),
                item.unpublishedAt().orElse(null),
                item.createdAt(),
                item.updatedAt(),
                jdbcVersion);
    }

    ContentItem toDomain(
            ContentItemPersistenceEntity entity,
            ContentItemRenderSnapshotTable.Row snapshot,
            List<ContentItemRenderedHeadingTable.Row> headings,
            Set<TagId> tagIds) {
        return ContentItem.rehydrate(
                ContentId.from(entity.id()),
                enumValue(ContentType.class, entity.type(), "ContentType"),
                enumValue(ContentStatus.class, entity.status(), "ContentStatus"),
                enumValue(ContentVisibility.class, entity.visibility(), "ContentVisibility"),
                enumValue(ContentLanguage.class, entity.language(), "ContentLanguage"),
                nullable(entity.slug(), Slug::ofCanonical),
                nullable(entity.title(), Title::of),
                nullable(entity.summary(), Summary::of),
                nullable(entity.markdownSource(), MarkdownSource::of),
                metadata(
                        entity.metaTitle(),
                        entity.metaDescription(),
                        entity.canonicalPath(),
                        entity.ogTitle(),
                        entity.ogDescription(),
                        entity.ogImageAssetId()),
                snapshot(snapshot, headings),
                tagIds,
                entity.publishedAt(),
                entity.unpublishedAt(),
                entity.createdAt(),
                entity.updatedAt(),
                Version.of(entity.version() == null ? 0L : entity.version()));
    }

    private Long previousJdbcVersion(Version version) {
        long value = version.value();
        if (value == 0L) {
            return 0L;
        }
        return value - 1L;
    }

    private ContentMetadata metadata(
            String seoTitle,
            String seoDescription,
            String canonicalPath,
            String openGraphTitle,
            String openGraphDescription,
            java.util.UUID ogImageAssetId) {
        return ContentMetadata.of(
                nullable(seoTitle, SeoTitle::of),
                nullable(seoDescription, SeoDescription::of),
                nullable(canonicalPath, CanonicalPath::of),
                nullable(openGraphTitle, OpenGraphTitle::of),
                nullable(openGraphDescription, OpenGraphDescription::of),
                nullable(ogImageAssetId, AssetId::from));
    }

    private ContentRenderSnapshot snapshot(
            ContentItemRenderSnapshotTable.Row snapshot,
            List<ContentItemRenderedHeadingTable.Row> headings) {
        if (snapshot == null) {
            return null;
        }
        return ContentRenderSnapshot.of(
                RenderedHtml.sanitized(snapshot.renderedHtml()),
                snapshot.renderedAt(),
                RendererVersion.of(snapshot.rendererVersion()),
                ReadingTime.minutes(snapshot.readingTimeMinutes()),
                snapshot.containsMermaid(),
                headings.stream()
                        .map(this::heading)
                        .toList());
    }

    private RenderedHeading heading(ContentItemRenderedHeadingTable.Row row) {
        return new RenderedHeading(
                HeadingLevel.of(row.level()),
                HeadingText.of(row.text()),
                HeadingAnchor.of(row.anchor()),
                SortOrder.of(row.position()));
    }

    private <T> T nullable(String value, java.util.function.Function<String, T> mapper) {
        return value == null ? null : mapper.apply(value);
    }

    private <T> T nullable(java.util.UUID value, java.util.function.Function<java.util.UUID, T> mapper) {
        return value == null ? null : mapper.apply(value);
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, String value, String label) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new ContentPublishingPersistenceException("Unknown persisted " + label + ": " + value, exception);
        }
    }
}
