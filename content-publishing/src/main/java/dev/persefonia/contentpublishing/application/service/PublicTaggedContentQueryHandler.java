package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.port.PublicTaggedContentReadModel;
import dev.persefonia.contentpublishing.application.query.PublicTaggedContentItem;
import dev.persefonia.contentpublishing.application.query.PublicTaggedContentQuery;
import java.util.List;
import java.util.Objects;

public final class PublicTaggedContentQueryHandler {
    private final PublicTaggedContentReadModel readModel;

    public PublicTaggedContentQueryHandler(PublicTaggedContentReadModel readModel) {
        this.readModel = Objects.requireNonNull(readModel, "readModel");
    }

    public List<PublicTaggedContentItem> list(PublicTaggedContentQuery query) {
        Objects.requireNonNull(query, "query");
        return readModel.list(query);
    }
}
