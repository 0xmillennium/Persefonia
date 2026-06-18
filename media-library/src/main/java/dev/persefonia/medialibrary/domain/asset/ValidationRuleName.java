package dev.persefonia.medialibrary.domain.asset;

public record ValidationRuleName(String value) {
    public ValidationRuleName {
        AssetValues.nonBlank(value, "validation rule name");
    }

    public static ValidationRuleName of(String value) {
        return new ValidationRuleName(value);
    }
}
