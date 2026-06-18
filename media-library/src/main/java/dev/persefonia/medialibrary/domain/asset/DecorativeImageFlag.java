package dev.persefonia.medialibrary.domain.asset;

public record DecorativeImageFlag(boolean value) {
    public static DecorativeImageFlag decorative() {
        return new DecorativeImageFlag(true);
    }

    public static DecorativeImageFlag informative() {
        return new DecorativeImageFlag(false);
    }
}
