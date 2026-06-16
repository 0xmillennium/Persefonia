package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.application.query.PublicContentPageResult;
import dev.persefonia.contentpublishing.application.query.PublicTranslationLinkSet;

public interface PublicTranslationReadModel {
    PublicTranslationLinkSet linksFor(PublicContentPageResult currentPage);
}
