package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.EDITOR;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.NOW;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.command.CreateContentDraftCommand;
import dev.persefonia.contentpublishing.application.event.ContentCreated;
import dev.persefonia.contentpublishing.application.event.ContentDraftUpdated;
import dev.persefonia.contentpublishing.application.event.ContentSlugChanged;
import dev.persefonia.contentpublishing.application.event.ContentVisibilityChanged;
import dev.persefonia.contentpublishing.application.exception.ContentCommandRejectedException;
import dev.persefonia.contentpublishing.application.service.ContentDraftCommandHandler;
import dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures;
import dev.persefonia.contentpublishing.application.support.InMemoryContentItemRepository;
import dev.persefonia.contentpublishing.application.support.RecordingContentPublishingEventPublisher;
import dev.persefonia.contentpublishing.application.support.TestContentAuthorizationPolicy;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import org.junit.jupiter.api.Test;

class ContentDraftCommandHandlerTest {
    private final InMemoryContentItemRepository items = new InMemoryContentItemRepository();
    private final TestContentAuthorizationPolicy authorization = new TestContentAuthorizationPolicy();
    private final RecordingContentPublishingEventPublisher events = new RecordingContentPublishingEventPublisher();
    private final ContentDraftCommandHandler handler = new ContentDraftCommandHandler(items, authorization, events);

    @Test
    void createsIncompleteDraftAndEmitsEventAfterSave() {
        var result = handler.create(new CreateContentDraftCommand(
                OWNER, ContentType.ARTICLE, ContentVisibility.PRIVATE, ContentLanguage.TR, NOW));

        assertThat(result.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(result.slug()).isEmpty();
        assertThat(items.saveCount()).isEqualTo(1);
        assertThat(events.events()).singleElement().isInstanceOf(ContentCreated.class);
    }

    @Test
    void updatesEditableDraftAndEmitsSpecificChangeEvents() {
        var item = ContentApplicationFixtures.completeDraft();
        items.add(item);

        var result = handler.update(ContentApplicationFixtures.titleAndRouteUpdate(item));

        assertThat(result.slug().orElseThrow().value()).isEqualTo("updated-route");
        assertThat(result.visibility()).isEqualTo(ContentVisibility.UNLISTED);
        assertThat(events.events())
                .extracting(Object::getClass)
                .containsExactly(ContentDraftUpdated.class, ContentSlugChanged.class, ContentVisibilityChanged.class);
    }

    @Test
    void rejectsPublishedDirectEditAndAuthorizationFailureBeforeSave() {
        var published = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        items.add(published);
        assertThatThrownBy(() -> handler.update(ContentApplicationFixtures.titleAndRouteUpdate(published)))
                .isInstanceOf(ContentCommandRejectedException.class);

        assertThatThrownBy(() -> handler.create(new CreateContentDraftCommand(
                EDITOR, ContentType.ARTICLE, ContentVisibility.PUBLIC, ContentLanguage.EN, NOW)))
                .isInstanceOf(SecurityException.class);
        assertThat(items.saveCount()).isZero();
        assertThat(events.events()).isEmpty();
    }

    @Test
    void updatesUnpublishedContent() {
        var unpublished = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        unpublished.unpublish(NOW.minusSeconds(60));
        items.add(unpublished);

        var result = handler.update(ContentApplicationFixtures.titleAndRouteUpdate(unpublished));

        assertThat(result.status()).isEqualTo(ContentStatus.UNPUBLISHED);
        assertThat(result.title().orElseThrow().value()).isEqualTo("Updated title");
        assertThat(items.saveCount()).isEqualTo(1);
    }

    @Test
    void rejectsArchivedContentUpdate() {
        var archived = ContentItemTestFixtures.completeDraft();
        archived.archive(NOW.minusSeconds(60));
        items.add(archived);

        assertThatThrownBy(() -> handler.update(ContentApplicationFixtures.titleAndRouteUpdate(archived)))
                .isInstanceOf(ContentCommandRejectedException.class)
                .hasMessageContaining("draft or unpublished");
        assertThat(items.saveCount()).isZero();
    }
}
