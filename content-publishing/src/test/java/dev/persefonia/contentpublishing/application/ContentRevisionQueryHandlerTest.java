package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.OWNER;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.query.ListContentRevisionsQuery;
import dev.persefonia.contentpublishing.application.service.ContentRevisionQueryHandler;
import dev.persefonia.contentpublishing.application.support.InMemoryContentRevisionRepository;
import dev.persefonia.contentpublishing.application.support.TestContentAuthorizationPolicy;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.support.ContentRevisionTestFixtures;
import org.junit.jupiter.api.Test;

class ContentRevisionQueryHandlerTest {
    @Test
    void returnsRevisionsOrderedWithoutMutationOrEvents() {
        var revisions = new InMemoryContentRevisionRepository();
        ContentRevision baseline = ContentRevisionTestFixtures.publishRevision();
        revisions.save(ContentRevision.publishSnapshot(
                ContentRevisionId.newId(), baseline.contentId(), RevisionNumber.of(2),
                ContentRevisionTestFixtures.completeSnapshot(), baseline.createdBy(), baseline.createdAt(), null));
        revisions.save(baseline);

        var results = new ContentRevisionQueryHandler(revisions, new TestContentAuthorizationPolicy())
                .list(new ListContentRevisionsQuery(OWNER, baseline.contentId()));

        assertThat(results).extracting(result -> result.revisionNumber().value()).containsExactly(1, 2);
    }
}
