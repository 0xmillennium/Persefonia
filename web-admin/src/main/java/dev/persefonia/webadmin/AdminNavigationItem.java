package dev.persefonia.webadmin;

import java.util.Objects;

public record AdminNavigationItem(
        String label,
        String href,
        boolean active,
        boolean disabled) {
    public AdminNavigationItem {
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (!disabled && (href == null || href.isBlank())) {
            throw new IllegalArgumentException("href is required for enabled navigation items");
        }
        if (active && disabled) {
            throw new IllegalArgumentException("active navigation item must not be disabled");
        }
    }

    public static AdminNavigationItem activeLink(String label, String href) {
        return new AdminNavigationItem(label, href, true, false);
    }

    public static AdminNavigationItem disabled(String label) {
        return new AdminNavigationItem(label, null, false, true);
    }
}
