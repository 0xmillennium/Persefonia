package dev.persefonia.medialibrary.domain.asset;

public record PublicAssetUrl(String value) {
    public PublicAssetUrl {
        AssetValues.nonBlank(value, "public asset URL");
        if (!value.startsWith("/") || value.startsWith("//")) {
            throw new AssetValidationException("public asset URL must be site-root relative");
        }
    }

    public static PublicAssetUrl of(String value) {
        return new PublicAssetUrl(value);
    }
}
