package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.EDITOR;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.NOW;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.command.PreviewContentCommand;
import dev.persefonia.contentpublishing.application.exception.ContentNotFoundException;
import dev.persefonia.contentpublishing.application.service.ContentPreviewQueryHandler;
import dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures;
import dev.persefonia.contentpublishing.application.support.FakeMarkdownRenderingService;
import dev.persefonia.contentpublishing.application.support.InMemoryContentItemRepository;
import dev.persefonia.contentpublishing.application.support.TestContentAuthorizationPolicy;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import org.junit.jupiter.api.Test;

class ContentPreviewQueryHandlerTest {
    private final InMemoryContentItemRepository items = new InMemoryContentItemRepository();
    private final FakeMarkdownRenderingService renderer = new FakeMarkdownRenderingService();
    private final ContentPreviewQueryHandler handler =
            new ContentPreviewQueryHandler(items, renderer, new TestContentAuthorizationPolicy());

    @Test
    void rendersSanitizedSnapshotWithoutSaving() {
        var item = ContentApplicationFixtures.completeDraft();
        items.add(item);

        var result = handler.preview(new PreviewContentCommand(OWNER, item.id(), NOW));

        assertThat(result.snapshot().renderedHtml().value()).contains("id=\"rendered\"");
        assertThat(result.snapshot().containsMermaid()).isTrue();
        assertThat(renderer.renderCount()).isEqualTo(1);
        assertThat(items.saveCount()).isZero();
    }

    @Test
    void authorizationAndMissingContentFailBeforeRendering() {
        ContentId missing = ContentId.newId();
        assertThatThrownBy(() -> handler.preview(new PreviewContentCommand(EDITOR, missing, NOW)))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> handler.preview(new PreviewContentCommand(OWNER, missing, NOW)))
                .isInstanceOf(ContentNotFoundException.class);
        assertThat(renderer.renderCount()).isZero();
    }
}
