package dev.persefonia.taxonomy.application.discovery;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class TagPublicRouteFactory {
    public PublicUrl publicUrl(DiscoveryLanguage language, TagSlug slug) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
        return new PublicUrl("/" + language.name().toLowerCase(Locale.ROOT) + "/tags/" + slug.value());
    }

    public List<PublicUrl> allLanguageRoutes(TagSlug slug) {
        return List.of(publicUrl(DiscoveryLanguage.TR, slug), publicUrl(DiscoveryLanguage.EN, slug));
    }
}
