package dev.persefonia.medialibrary.domain.asset;

public record ValidationMessage(String value) {
    public ValidationMessage {
        AssetValues.nonBlank(value, "validation message");
    }

    public static ValidationMessage of(String value) {
        return new ValidationMessage(value);
    }
}
