package dev.persefonia.webpublic.series;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import java.util.Locale;
import java.util.Objects;

public record PublicSeriesRoute(DiscoveryLanguage language, String slug) {
    public PublicSeriesRoute {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
    }

    public String publicPath() {
        return "/" + language.name().toLowerCase(Locale.ROOT) + "/series/" + slug;
    }
}
