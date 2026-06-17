package dev.persefonia.webpublic.projects;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import java.util.Locale;
import java.util.Objects;

public record PublicProjectRoute(DiscoveryLanguage language, String slug) {
    public PublicProjectRoute {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
    }

    public String publicPath() {
        return "/" + language.name().toLowerCase(Locale.ROOT) + "/projects/" + slug;
    }
}
