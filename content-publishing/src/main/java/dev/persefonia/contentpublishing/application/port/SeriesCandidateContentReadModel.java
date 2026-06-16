package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.application.query.SeriesCandidateContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import java.util.List;

public interface SeriesCandidateContentReadModel {
    List<SeriesCandidateContentItem> candidatesFor(SeriesId seriesId, ContentLanguage language);
}
