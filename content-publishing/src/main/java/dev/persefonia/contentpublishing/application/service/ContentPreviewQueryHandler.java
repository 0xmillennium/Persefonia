package dev.persefonia.contentpublishing.application.service;

import static dev.persefonia.contentpublishing.application.service.ContentApplicationSupport.requiredContent;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.command.ContentPreviewResult;
import dev.persefonia.contentpublishing.application.command.PreviewContentCommand;
import dev.persefonia.contentpublishing.application.exception.ContentCommandRejectedException;
import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingService;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.util.Objects;

public final class ContentPreviewQueryHandler {
    private final ContentItemRepository contentItems;
    private final MarkdownRenderingService renderer;
    private final ContentCommandAuthorizationPolicy authorization;

    public ContentPreviewQueryHandler(
            ContentItemRepository contentItems,
            MarkdownRenderingService renderer,
            ContentCommandAuthorizationPolicy authorization) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public ContentPreviewResult preview(PreviewContentCommand command) {
        authorization.requireOwner(command.actor(), "content.preview");
        ContentItem item = requiredContent(contentItems, command.contentId());
        var source = item.markdownSource()
                .orElseThrow(() -> new ContentCommandRejectedException("Content requires markdown source for preview"));
        return new ContentPreviewResult(item.id(), renderer.render(source, command.requestedAt()));
    }
}
