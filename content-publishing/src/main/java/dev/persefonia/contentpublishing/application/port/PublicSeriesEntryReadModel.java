package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.application.query.PublicSeriesEntryItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import java.util.List;

public interface PublicSeriesEntryReadModel {
    List<PublicSeriesEntryItem> listEntries(SeriesId seriesId, ContentLanguage language);
}
