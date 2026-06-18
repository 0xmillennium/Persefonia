package dev.persefonia.medialibrary.domain.asset;

public record OriginalFilename(String value) {
    public OriginalFilename {
        AssetValues.nonBlank(value, "original filename");
    }

    public static OriginalFilename of(String value) {
        return new OriginalFilename(value);
    }
}
