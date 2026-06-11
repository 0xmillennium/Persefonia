package dev.persefonia.app.contentpublishing.persistence.spike;

final class SpikeContentItemMapper {
    SpikeContentItemEntity toItemEntity(SpikeContentItem item) {
        return new SpikeContentItemEntity(
                item.id(),
                item.type(),
                item.status(),
                item.visibility(),
                item.language(),
                item.slug(),
                item.title(),
                item.summary(),
                item.markdownSource(),
                item.metaTitle(),
                item.metaDescription(),
                item.canonicalPath(),
                item.ogTitle(),
                item.ogDescription(),
                item.ogImageAssetId(),
                item.publishedAt(),
                item.unpublishedAt(),
                item.createdAt(),
                item.updatedAt(),
                item.version());
    }

    SpikeContentItem toItem(SpikeContentItemEntity entity, SpikeContentRenderSnapshot snapshot) {
        return new SpikeContentItem(
                entity.id(),
                entity.type(),
                entity.status(),
                entity.visibility(),
                entity.language(),
                entity.slug(),
                entity.title(),
                entity.summary(),
                entity.markdownSource(),
                entity.metaTitle(),
                entity.metaDescription(),
                entity.canonicalPath(),
                entity.ogTitle(),
                entity.ogDescription(),
                entity.ogImageAssetId(),
                entity.publishedAt(),
                entity.unpublishedAt(),
                entity.createdAt(),
                entity.updatedAt(),
                entity.version(),
                snapshot);
    }

    SpikeContentRevisionEntity toRevisionEntity(SpikeContentRevision revision) {
        return new SpikeContentRevisionEntity(
                revision.id(),
                revision.contentItemId(),
                revision.revisionNumber(),
                revision.revisionType(),
                revision.title(),
                revision.slug(),
                revision.summary(),
                revision.markdownSource(),
                revision.renderedHtml(),
                revision.metaTitle(),
                revision.metaDescription(),
                revision.canonicalPath(),
                revision.ogTitle(),
                revision.ogDescription(),
                revision.ogImageAssetId(),
                revision.createdByAdminRef(),
                revision.createdAt(),
                revision.changeNote());
    }

    SpikeContentRevision toRevision(SpikeContentRevisionEntity entity) {
        return new SpikeContentRevision(
                entity.id(),
                entity.contentItemId(),
                entity.revisionNumber(),
                entity.revisionType(),
                entity.title(),
                entity.slug(),
                entity.summary(),
                entity.markdownSource(),
                entity.renderedHtml(),
                entity.metaTitle(),
                entity.metaDescription(),
                entity.canonicalPath(),
                entity.ogTitle(),
                entity.ogDescription(),
                entity.ogImageAssetId(),
                entity.createdByAdminRef(),
                entity.createdAt(),
                entity.changeNote());
    }
}
