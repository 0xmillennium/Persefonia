package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.NOW;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.OWNER;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.command.ArchiveContentCommand;
import dev.persefonia.contentpublishing.application.command.UnpublishContentCommand;
import dev.persefonia.contentpublishing.application.event.ContentArchived;
import dev.persefonia.contentpublishing.application.event.ContentUnpublished;
import dev.persefonia.contentpublishing.application.service.ContentLifecycleCommandHandler;
import dev.persefonia.contentpublishing.application.support.InMemoryContentItemRepository;
import dev.persefonia.contentpublishing.application.support.RecordingContentPublishingEventPublisher;
import dev.persefonia.contentpublishing.application.support.TestContentAuthorizationPolicy;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import org.junit.jupiter.api.Test;

class ContentLifecycleCommandHandlerTest {
    @Test
    void unpublishesThenArchivesWithoutCreatingRevisions() {
        var items = new InMemoryContentItemRepository();
        var events = new RecordingContentPublishingEventPublisher();
        var handler = new ContentLifecycleCommandHandler(items, new TestContentAuthorizationPolicy(), events);
        var item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        items.add(item);

        var unpublished = handler.unpublish(new UnpublishContentCommand(OWNER, item.id(), NOW));
        var archived = handler.archive(new ArchiveContentCommand(OWNER, item.id(), NOW.plusSeconds(60)));

        assertThat(unpublished.status()).isEqualTo(ContentStatus.UNPUBLISHED);
        assertThat(archived.status()).isEqualTo(ContentStatus.ARCHIVED);
        assertThat(item.publishedAt()).isPresent();
        assertThat(events.events()).extracting(Object::getClass)
                .containsExactly(ContentUnpublished.class, ContentArchived.class);
    }
}
