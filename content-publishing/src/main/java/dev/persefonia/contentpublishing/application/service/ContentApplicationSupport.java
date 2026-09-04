package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.command.ContentDraftResult;
import dev.persefonia.contentpublishing.application.exception.ContentNotFoundException;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicMutationFacts;

final class ContentApplicationSupport {
    private ContentApplicationSupport() {
    }

    static ContentItem requiredContent(ContentItemRepository repository, ContentId contentId) {
        return repository.findById(contentId).orElseThrow(() -> new ContentNotFoundException(contentId));
    }

    static ContentDraftResult draftResult(ContentItem item, ContentPublicMutationFacts facts) {
        return new ContentDraftResult(
                item.id(),
                item.status(),
                item.visibility(),
                item.language(),
                item.slug(),
                item.title(),
                item.summary(),
                item.createdAt(),
                item.updatedAt(),
                item.version(),
                facts);
    }
}
