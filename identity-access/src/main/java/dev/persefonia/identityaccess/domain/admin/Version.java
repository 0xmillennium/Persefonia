package dev.persefonia.identityaccess.domain.admin;

public record Version(long value) {
    public Version {
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative");
        }
    }

    public static Version initial() {
        return new Version(0);
    }

    public static Version of(long value) {
        return new Version(value);
    }

    public Version next() {
        return new Version(Math.addExact(value, 1));
    }
}
