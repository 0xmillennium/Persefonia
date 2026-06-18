package dev.persefonia.medialibrary.domain.asset;

public record Checksum(String value) {
    public Checksum {
        AssetValues.nonBlank(value, "checksum");
    }

    public static Checksum of(String value) {
        return new Checksum(value);
    }
}
