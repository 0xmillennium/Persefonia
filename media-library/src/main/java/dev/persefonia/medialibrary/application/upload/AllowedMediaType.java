package dev.persefonia.medialibrary.application.upload;

import dev.persefonia.medialibrary.domain.asset.AssetKind;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum AllowedMediaType {
    JPEG("image/jpeg", "jpg", Set.of("jpg", "jpeg"), AssetKind.IMAGE),
    PNG("image/png", "png", Set.of("png"), AssetKind.IMAGE),
    PDF("application/pdf", "pdf", Set.of("pdf"), AssetKind.PDF);

    private final String contentType;
    private final String canonicalExtension;
    private final Set<String> declaredExtensions;
    private final AssetKind assetKind;

    AllowedMediaType(
            String contentType,
            String canonicalExtension,
            Set<String> declaredExtensions,
            AssetKind assetKind) {
        this.contentType = contentType;
        this.canonicalExtension = canonicalExtension;
        this.declaredExtensions = declaredExtensions;
        this.assetKind = assetKind;
    }

    public String contentType() {
        return contentType;
    }

    public String canonicalExtension() {
        return canonicalExtension;
    }

    public boolean acceptsExtension(String extension) {
        return extension != null && declaredExtensions.contains(normalizeExtension(extension));
    }

    public AssetKind assetKind() {
        return assetKind;
    }

    public static Optional<AllowedMediaType> fromContentType(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.contentType.equals(normalized))
                .findFirst();
    }

    public static String normalizeExtension(String extension) {
        String normalized = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }
}
