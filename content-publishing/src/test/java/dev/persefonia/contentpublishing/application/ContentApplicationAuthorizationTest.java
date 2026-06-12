package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.EDITOR;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.command.ArchiveContentCommand;
import dev.persefonia.contentpublishing.application.command.CreateContentDraftCommand;
import dev.persefonia.contentpublishing.application.command.PreviewContentCommand;
import dev.persefonia.contentpublishing.application.command.PublishContentCommand;
import dev.persefonia.contentpublishing.application.command.UnpublishContentCommand;
import dev.persefonia.contentpublishing.application.command.UpdateContentDraftCommand;
import dev.persefonia.contentpublishing.application.query.ListContentRevisionsQuery;
import dev.persefonia.contentpublishing.application.service.ContentDraftCommandHandler;
import dev.persefonia.contentpublishing.application.service.ContentLifecycleCommandHandler;
import dev.persefonia.contentpublishing.application.service.ContentPreviewQueryHandler;
import dev.persefonia.contentpublishing.application.service.ContentPublishCommandHandler;
import dev.persefonia.contentpublishing.application.service.ContentRevisionQueryHandler;
import dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures;
import dev.persefonia.contentpublishing.application.support.FakeMarkdownRenderingService;
import dev.persefonia.contentpublishing.application.support.InMemoryContentItemRepository;
import dev.persefonia.contentpublishing.application.support.InMemoryContentRevisionRepository;
import dev.persefonia.contentpublishing.application.support.RecordingContentPublishingEventPublisher;
import dev.persefonia.contentpublishing.application.support.TestContentAuthorizationPolicy;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import org.junit.jupiter.api.Test;

class ContentApplicationAuthorizationTest {
    @Test
    void nonOwnerCannotExecuteAnyApplicationOperation() {
        var items = new InMemoryContentItemRepository();
        var revisions = new InMemoryContentRevisionRepository();
        var renderer = new FakeMarkdownRenderingService();
        var events = new RecordingContentPublishingEventPublisher();
        var authorization = new TestContentAuthorizationPolicy();
        var item = ContentApplicationFixtures.completeDraft();
        items.add(item);
        var drafts = new ContentDraftCommandHandler(items, authorization, events);
        var previews = new ContentPreviewQueryHandler(items, renderer, authorization);
        var publishing = new ContentPublishCommandHandler(items, revisions, renderer, authorization, events);
        var lifecycle = new ContentLifecycleCommandHandler(items, authorization, events);
        var revisionQueries = new ContentRevisionQueryHandler(items, revisions, authorization);

        assertDenied(() -> drafts.create(new CreateContentDraftCommand(
                EDITOR, ContentType.ARTICLE, ContentVisibility.PUBLIC, ContentLanguage.EN, NOW)));
        var update = ContentApplicationFixtures.titleAndRouteUpdate(item);
        assertDenied(() -> drafts.update(new UpdateContentDraftCommand(
                EDITOR,
                update.contentId(),
                update.slug(),
                update.title(),
                update.summary(),
                update.markdownSource(),
                update.metadata(),
                update.visibility(),
                update.requestedAt())));
        assertDenied(() -> previews.preview(new PreviewContentCommand(EDITOR, item.id(), NOW)));
        assertDenied(() -> publishing.publish(new PublishContentCommand(EDITOR, item.id(), NOW, null)));
        assertDenied(() -> lifecycle.unpublish(new UnpublishContentCommand(EDITOR, item.id(), NOW)));
        assertDenied(() -> lifecycle.archive(new ArchiveContentCommand(EDITOR, item.id(), NOW)));
        assertDenied(() -> revisionQueries.list(new ListContentRevisionsQuery(EDITOR, item.id())));

        assertThat(items.saveCount()).isZero();
        assertThat(renderer.renderCount()).isZero();
        assertThat(events.events()).isEmpty();
        assertThat(revisions.all()).isEmpty();
    }

    private void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(SecurityException.class);
    }
}
