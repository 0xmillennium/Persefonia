package dev.persefonia.medialibrary.domain.asset;

public record FileSize(long value) {
    public FileSize {
        AssetValues.positive(value, "file size");
    }

    public static FileSize of(long value) {
        return new FileSize(value);
    }
}
