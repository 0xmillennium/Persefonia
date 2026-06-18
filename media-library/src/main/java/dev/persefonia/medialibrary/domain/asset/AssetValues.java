package dev.persefonia.medialibrary.domain.asset;

import java.util.Objects;

final class AssetValues {
    private AssetValues() {
    }

    static String nonBlank(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new AssetValidationException(label + " must not be blank");
        }
        return value;
    }

    static long positive(long value, String label) {
        if (value <= 0) {
            throw new AssetValidationException(label + " must be positive");
        }
        return value;
    }

    static int positive(int value, String label) {
        if (value <= 0) {
            throw new AssetValidationException(label + " must be positive");
        }
        return value;
    }
}
