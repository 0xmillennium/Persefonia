package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.port.PublicTranslationReadModel;
import dev.persefonia.contentpublishing.application.query.PublicContentPageResult;
import dev.persefonia.contentpublishing.application.query.PublicTranslationLinkSet;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import java.util.Objects;

public final class PublicTranslationLinkQueryService {
    private final PublicTranslationReadModel readModel;

    public PublicTranslationLinkQueryService(PublicTranslationReadModel readModel) {
        this.readModel = Objects.requireNonNull(readModel, "readModel");
    }

    public PublicTranslationLinkSet linksFor(PublicContentPageResult currentPage) {
        Objects.requireNonNull(currentPage, "currentPage");
        if (currentPage.visibility() != ContentVisibility.PUBLIC) {
            return PublicTranslationLinkSet.empty();
        }
        return readModel.linksFor(currentPage);
    }
}
