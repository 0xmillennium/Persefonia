package dev.persefonia.contentpublishing.domain.revision;

import dev.persefonia.contentpublishing.domain.content.AssetId;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.OpenGraphDescription;
import dev.persefonia.contentpublishing.domain.content.OpenGraphTitle;
import dev.persefonia.contentpublishing.domain.content.SeoDescription;
import dev.persefonia.contentpublishing.domain.content.SeoTitle;
import java.util.Objects;
import java.util.Optional;

public final class RevisionMetadata {
    private final SeoTitle seoTitle;
    private final SeoDescription seoDescription;
    private final CanonicalPath canonicalPath;
    private final OpenGraphTitle openGraphTitle;
    private final OpenGraphDescription openGraphDescription;
    private final AssetId ogImageAssetId;

    private RevisionMetadata(
            SeoTitle seoTitle,
            SeoDescription seoDescription,
            CanonicalPath canonicalPath,
            OpenGraphTitle openGraphTitle,
            OpenGraphDescription openGraphDescription,
            AssetId ogImageAssetId) {
        this.seoTitle = seoTitle;
        this.seoDescription = seoDescription;
        this.canonicalPath = canonicalPath;
        this.openGraphTitle = openGraphTitle;
        this.openGraphDescription = openGraphDescription;
        this.ogImageAssetId = ogImageAssetId;
    }

    public static RevisionMetadata from(ContentMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        return new RevisionMetadata(
                metadata.seoTitle().orElse(null),
                metadata.seoDescription().orElse(null),
                metadata.canonicalPath().orElse(null),
                metadata.openGraphTitle().orElse(null),
                metadata.openGraphDescription().orElse(null),
                metadata.ogImageAssetId().orElse(null));
    }

    public static RevisionMetadata of(
            SeoTitle seoTitle,
            SeoDescription seoDescription,
            CanonicalPath canonicalPath,
            OpenGraphTitle openGraphTitle,
            OpenGraphDescription openGraphDescription,
            AssetId ogImageAssetId) {
        return new RevisionMetadata(seoTitle, seoDescription, canonicalPath, openGraphTitle, openGraphDescription, ogImageAssetId);
    }

    public Optional<SeoTitle> seoTitle() {
        return Optional.ofNullable(seoTitle);
    }

    public Optional<SeoDescription> seoDescription() {
        return Optional.ofNullable(seoDescription);
    }

    public Optional<CanonicalPath> canonicalPath() {
        return Optional.ofNullable(canonicalPath);
    }

    public Optional<OpenGraphTitle> openGraphTitle() {
        return Optional.ofNullable(openGraphTitle);
    }

    public Optional<OpenGraphDescription> openGraphDescription() {
        return Optional.ofNullable(openGraphDescription);
    }

    public Optional<AssetId> ogImageAssetId() {
        return Optional.ofNullable(ogImageAssetId);
    }
}
