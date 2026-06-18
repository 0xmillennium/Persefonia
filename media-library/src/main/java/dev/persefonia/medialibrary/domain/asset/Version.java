package dev.persefonia.medialibrary.domain.asset;

public record Version(long value) {
    public Version {
        if (value < 0) {
            throw new AssetValidationException("version must not be negative");
        }
    }

    public static Version initial() {
        return new Version(0);
    }

    public static Version of(long value) {
        return new Version(value);
    }

    public Version next() {
        return new Version(value + 1);
    }
}
