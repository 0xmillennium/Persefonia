package dev.persefonia.medialibrary.domain.asset;

public record FileExtension(String value) {
    public FileExtension {
        AssetValues.nonBlank(value, "file extension");
    }

    public static FileExtension of(String value) {
        return new FileExtension(value);
    }
}
