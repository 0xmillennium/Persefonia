package dev.persefonia.medialibrary.domain.asset;

public enum VariantName {
    THUMBNAIL("thumbnail"),
    MEDIUM("medium"),
    LARGE("large"),
    OG("og");

    private final String databaseValue;

    VariantName(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static VariantName fromDatabaseValue(String value) {
        for (VariantName name : values()) {
            if (name.databaseValue.equals(value)) {
                return name;
            }
        }
        throw new AssetValidationException("unknown variant name: " + value);
    }
}
