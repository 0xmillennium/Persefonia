package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.application.port.ReferencedTagDetails;
import java.util.List;

/** Source-owned relationships; consumers must still resolve the public destination routes. */
public record PublicContentNavigation(List<ReferencedTagDetails> tags, List<SeriesLink> series) {
    public PublicContentNavigation {
        tags = List.copyOf(tags);
        series = List.copyOf(series);
    }

    public record SeriesLink(java.util.UUID id, String title, String slug, String publicPath) {}
}
