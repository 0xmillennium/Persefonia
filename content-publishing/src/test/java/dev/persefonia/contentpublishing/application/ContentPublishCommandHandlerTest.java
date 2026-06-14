package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.NOW;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.command.PublishContentCommand;
import dev.persefonia.contentpublishing.application.event.ContentPublished;
import dev.persefonia.contentpublishing.application.event.PublishedContentChanged;
import dev.persefonia.contentpublishing.application.service.ContentPublishCommandHandler;
import dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures;
import dev.persefonia.contentpublishing.application.support.FakeMarkdownRenderingService;
import dev.persefonia.contentpublishing.application.support.InMemoryContentItemRepository;
import dev.persefonia.contentpublishing.application.support.InMemoryContentRevisionRepository;
import dev.persefonia.contentpublishing.application.support.NoopContentDiscoverabilityCoordinator;
import dev.persefonia.contentpublishing.application.support.RecordingContentPublishingEventPublisher;
import dev.persefonia.contentpublishing.application.support.TestContentAuthorizationPolicy;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.revision.ChangeNote;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.revision.RevisionType;
import dev.persefonia.contentpublishing.domain.revision.port.ContentRevisionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContentPublishCommandHandlerTest {
    private final InMemoryContentItemRepository items = new InMemoryContentItemRepository();
    private final InMemoryContentRevisionRepository revisions = new InMemoryContentRevisionRepository();
    private final FakeMarkdownRenderingService renderer = new FakeMarkdownRenderingService();
    private final RecordingContentPublishingEventPublisher events = new RecordingContentPublishingEventPublisher();
    private final ContentPublishCommandHandler handler = new ContentPublishCommandHandler(
            items, revisions, renderer, new TestContentAuthorizationPolicy(), events,
            NoopContentDiscoverabilityCoordinator.create());

    @Test
    void publishesWithCompleteSanitizedRevisionAndSpecificEvents() {
        var item = ContentApplicationFixtures.completeDraft();
        items.add(item);

        var first = handler.publish(new PublishContentCommand(OWNER, item.id(), NOW, ChangeNote.of("First publish")));
        var second = handler.publish(new PublishContentCommand(OWNER, item.id(), NOW.plusSeconds(60), null));

        assertThat(first.status()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(second.revisionNumber().value()).isEqualTo(2);
        assertThat(revisions.all()).hasSize(2).allSatisfy(revision -> {
            assertThat(revision.revisionType()).isEqualTo(RevisionType.PUBLISH);
            assertThat(revision.renderedHtml().orElseThrow().value()).contains("id=\"rendered\"");
        });
        assertThat(events.eventTypes())
                .containsExactly(ContentPublished.class, PublishedContentChanged.class);
    }

    @Test
    void failedRevisionSaveEmitsNoEvent() {
        var item = ContentApplicationFixtures.completeDraft();
        items.add(item);
        ContentRevisionRepository failingRevisions = new ContentRevisionRepository() {
            @Override
            public ContentRevision save(ContentRevision revision) {
                throw new IllegalStateException("forced revision failure");
            }

            @Override
            public Optional<ContentRevision> findById(ContentRevisionId id) {
                return Optional.empty();
            }

            @Override
            public List<ContentRevision> findByContentId(dev.persefonia.contentpublishing.domain.content.ContentId contentId) {
                return List.of();
            }

            @Override
            public Optional<RevisionNumber> findLatestRevisionNumber(
                    dev.persefonia.contentpublishing.domain.content.ContentId contentId) {
                return Optional.empty();
            }
        };
        var failingHandler = new ContentPublishCommandHandler(
                items, failingRevisions, renderer, new TestContentAuthorizationPolicy(), events,
                NoopContentDiscoverabilityCoordinator.create());

        assertThatThrownBy(() -> failingHandler.publish(new PublishContentCommand(OWNER, item.id(), NOW, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(events.events()).isEmpty();
    }
}
