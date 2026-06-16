package dev.persefonia.webadmin.series;

import dev.persefonia.contentpublishing.application.query.SeriesListItem;
import java.util.List;
import java.util.Objects;

public record AdminSeriesListPage(
        AdminSeriesPageChrome chrome,
        List<SeriesListItem> series,
        String successMessage) {
    public AdminSeriesListPage {
        Objects.requireNonNull(chrome, "chrome");
        series = List.copyOf(Objects.requireNonNull(series, "series"));
    }
}
