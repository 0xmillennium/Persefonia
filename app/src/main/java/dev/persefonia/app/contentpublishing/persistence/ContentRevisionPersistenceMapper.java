package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.AssetId;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.OpenGraphDescription;
import dev.persefonia.contentpublishing.domain.content.OpenGraphTitle;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.content.SeoDescription;
import dev.persefonia.contentpublishing.domain.content.SeoTitle;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.revision.ChangeNote;
import dev.persefonia.contentpublishing.domain.revision.CompleteContentSnapshot;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionMetadata;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.revision.RevisionType;

final class ContentRevisionPersistenceMapper {
    ContentRevisionPersistenceEntity toEntity(ContentRevision revision) {
        return new ContentRevisionPersistenceEntity(
                revision.id().value(),
                revision.contentId().value(),
                revision.revisionNumber().value(),
                revision.revisionType().name(),
                revision.title().value(),
                revision.slug().value(),
                revision.summary().value(),
                revision.markdownSource().value(),
                revision.renderedHtml().map(RenderedHtml::value).orElse(null),
                revision.metadata().seoTitle().map(SeoTitle::value).orElse(null),
                revision.metadata().seoDescription().map(SeoDescription::value).orElse(null),
                revision.metadata().canonicalPath().map(CanonicalPath::value).orElse(null),
                revision.metadata().openGraphTitle().map(OpenGraphTitle::value).orElse(null),
                revision.metadata().openGraphDescription().map(OpenGraphDescription::value).orElse(null),
                revision.metadata().ogImageAssetId().map(AssetId::value).orElse(null),
                revision.createdBy().value(),
                revision.createdAt(),
                revision.changeNote().map(ChangeNote::value).orElse(null));
    }

    ContentRevision toDomain(ContentRevisionPersistenceEntity entity) {
        return ContentRevision.create(
                ContentRevisionId.from(entity.id()),
                ContentId.from(entity.contentItemId()),
                RevisionNumber.of(entity.revisionNumber()),
                enumValue(RevisionType.class, entity.revisionType(), "RevisionType"),
                CompleteContentSnapshot.of(
                        Title.of(entity.title()),
                        Slug.ofCanonical(entity.slug()),
                        Summary.of(entity.summary()),
                        MarkdownSource.of(entity.markdownSource()),
                        nullable(entity.renderedHtml(), RenderedHtml::sanitized),
                        metadata(entity)),
                AdminIdentityRef.from(entity.createdByAdminRef()),
                entity.createdAt(),
                nullable(entity.changeNote(), ChangeNote::of));
    }

    private RevisionMetadata metadata(ContentRevisionPersistenceEntity entity) {
        return RevisionMetadata.of(
                nullable(entity.metaTitle(), SeoTitle::of),
                nullable(entity.metaDescription(), SeoDescription::of),
                nullable(entity.canonicalPath(), CanonicalPath::of),
                nullable(entity.ogTitle(), OpenGraphTitle::of),
                nullable(entity.ogDescription(), OpenGraphDescription::of),
                nullable(entity.ogImageAssetId(), AssetId::from));
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
