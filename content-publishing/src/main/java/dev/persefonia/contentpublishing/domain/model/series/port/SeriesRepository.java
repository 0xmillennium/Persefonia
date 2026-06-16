package dev.persefonia.contentpublishing.domain.model.series.port;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSlug;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSummary;
import java.util.List;
import java.util.Optional;

public interface SeriesRepository {
    Series save(Series series);

    Optional<Series> findById(SeriesId id);

    Optional<Series> findByLanguageAndSlug(ContentLanguage language, SeriesSlug slug);

    boolean existsByLanguageAndSlug(ContentLanguage language, SeriesSlug slug);

    List<SeriesSummary> findAllForAdmin();
}
