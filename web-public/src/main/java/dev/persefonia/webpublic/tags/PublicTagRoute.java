package dev.persefonia.webpublic.tags;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import java.util.Objects;

public record PublicTagRoute(DiscoveryLanguage language, String slug) {
    public PublicTagRoute {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
    }

    public String publicPath() {
        return "/" + language.name().toLowerCase(java.util.Locale.ROOT) + "/tags/" + slug;
    }
}
