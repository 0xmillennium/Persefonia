package dev.persefonia.contentpublishing.application.discovery;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSlug;
import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.Locale;
import java.util.Objects;

public final class SeriesPublicRouteFactory {
    public PublicUrl publicUrl(Series series) {
        Objects.requireNonNull(series, "series");
        return publicUrl(series.language(), series.slug());
    }

    public PublicUrl publicUrl(ContentLanguage language, SeriesSlug slug) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
        return new PublicUrl("/" + language.name().toLowerCase(Locale.ROOT) + "/series/" + slug.value());
    }
}
