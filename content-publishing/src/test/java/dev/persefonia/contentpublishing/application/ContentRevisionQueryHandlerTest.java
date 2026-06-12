package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.exception.ContentNotFoundException;
import dev.persefonia.contentpublishing.application.query.ListContentRevisionsQuery;
import dev.persefonia.contentpublishing.application.service.ContentRevisionQueryHandler;
import dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures;
import dev.persefonia.contentpublishing.application.support.InMemoryContentItemRepository;
import dev.persefonia.contentpublishing.application.support.InMemoryContentRevisionRepository;
import dev.persefonia.contentpublishing.application.support.TestContentAuthorizationPolicy;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.support.ContentRevisionTestFixtures;
import org.junit.jupiter.api.Test;

class ContentRevisionQueryHandlerTest {
    @Test
    void returnsCompleteHistoryNewestFirstWithoutMutation() {
        var items = new InMemoryContentItemRepository();
        var revisions = new InMemoryContentRevisionRepository();
        ContentRevision baseline = ContentRevisionTestFixtures.publishRevision();
        var item = ContentApplicationFixtures.completeDraft();
        items.add(item);
        baseline = ContentRevision.publishSnapshot(
                baseline.id(), item.id(), baseline.revisionNumber(), ContentRevisionTestFixtures.completeSnapshot(),
                baseline.createdBy(), baseline.createdAt(), baseline.changeNote().orElse(null));
        revisions.save(ContentRevision.publishSnapshot(
                ContentRevisionId.newId(), baseline.contentId(), RevisionNumber.of(2),
                ContentRevisionTestFixtures.completeSnapshot(), baseline.createdBy(), baseline.createdAt(), null));
        revisions.save(baseline);

        var results = new ContentRevisionQueryHandler(items, revisions, new TestContentAuthorizationPolicy())
                .history(new ListContentRevisionsQuery(OWNER, baseline.contentId()));

        assertThat(results.contentTitle()).contains("Content baseline");
        assertThat(results.revisions()).extracting(result -> result.revisionNumber()).containsExactly(2, 1);
        assertThat(results.revisions()).allSatisfy(result -> {
            assertThat(result.title()).isEqualTo("Revision title");
            assertThat(result.slug()).isEqualTo("revision-title");
            assertThat(result.renderedHtmlPresent()).isTrue();
        });
        assertThat(items.saveCount()).isZero();
    }

    @Test
    void ownerAuthorizationAndExistingContentAreRequired() {
        var items = new InMemoryContentItemRepository();
        var handler = new ContentRevisionQueryHandler(
                items, new InMemoryContentRevisionRepository(), new TestContentAuthorizationPolicy());

        assertThatThrownBy(() -> handler.history(new ListContentRevisionsQuery(
                        ContentApplicationFixtures.EDITOR, ContentId.newId())))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> handler.history(new ListContentRevisionsQuery(OWNER, ContentId.newId())))
                .isInstanceOf(ContentNotFoundException.class);
    }
}
