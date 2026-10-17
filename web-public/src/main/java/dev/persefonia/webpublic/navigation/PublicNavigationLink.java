package dev.persefonia.webpublic.navigation;

import java.util.Objects;

public record PublicNavigationLink(String label, String url) {
    public PublicNavigationLink {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(url, "url");
        if (!url.startsWith("/") || url.startsWith("//")) {
            throw new IllegalArgumentException("Navigation requires a local public path");
        }
    }
}
