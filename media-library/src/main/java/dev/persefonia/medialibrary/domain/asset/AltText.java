package dev.persefonia.medialibrary.domain.asset;

public record AltText(String value) {
    public AltText {
        AssetValues.nonBlank(value, "alt text");
    }

    public static AltText of(String value) {
        return new AltText(value);
    }
}
