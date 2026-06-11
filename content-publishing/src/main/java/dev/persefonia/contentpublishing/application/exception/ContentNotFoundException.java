package dev.persefonia.contentpublishing.application.exception;

import dev.persefonia.contentpublishing.domain.content.ContentId;

public final class ContentNotFoundException extends ContentApplicationException {
    public ContentNotFoundException(ContentId contentId) {
        super("Content item not found: " + contentId.value());
    }
}
