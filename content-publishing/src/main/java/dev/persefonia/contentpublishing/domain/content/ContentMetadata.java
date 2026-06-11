package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;
import java.util.Optional;

public final class ContentMetadata {
    private final SeoTitle seoTitle;
    private final SeoDescription seoDescription;
    private final CanonicalPath canonicalPath;
    private final OpenGraphTitle openGraphTitle;
    private final OpenGraphDescription openGraphDescription;
    private final AssetId ogImageAssetId;

    private ContentMetadata(
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

    public static ContentMetadata empty() {
        return new ContentMetadata(null, null, null, null, null, null);
    }

    public static ContentMetadata of(
            SeoTitle seoTitle,
            SeoDescription seoDescription,
            CanonicalPath canonicalPath,
            OpenGraphTitle openGraphTitle,
            OpenGraphDescription openGraphDescription,
            AssetId ogImageAssetId) {
        return new ContentMetadata(
                seoTitle,
                seoDescription,
                canonicalPath,
                openGraphTitle,
                openGraphDescription,
                ogImageAssetId);
    }

    public static ContentMetadata withCanonicalPath(CanonicalPath canonicalPath) {
        return new ContentMetadata(null, null, Objects.requireNonNull(canonicalPath, "canonicalPath"), null, null, null);
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
