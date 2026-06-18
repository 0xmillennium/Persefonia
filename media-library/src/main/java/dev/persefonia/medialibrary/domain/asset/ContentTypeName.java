package dev.persefonia.medialibrary.domain.asset;

public record ContentTypeName(String value) {
    public ContentTypeName {
        AssetValues.nonBlank(value, "content type");
    }

    public static ContentTypeName of(String value) {
        return new ContentTypeName(value);
    }
}
