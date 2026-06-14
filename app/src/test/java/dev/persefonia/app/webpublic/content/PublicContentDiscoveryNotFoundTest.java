package dev.persefonia.app.webpublic.content;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicContentDiscoveryNotFoundTest extends PublicContentMvcTestSupport {
    @Test
    void contentExistsButNoDiscoveryProjectionReturns404() throws Exception {
        items.add(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "missing-projection"));

        assertSafeNotFound("/tr/articles/missing-projection");
    }

    @Test
    void discoveryProjectionExistsButContentMissingReturns404() throws Exception {
        routes.addFound("/tr/articles/missing-content", UUID.randomUUID());

        assertSafeNotFound("/tr/articles/missing-content");
    }

    @Test
    void projectionExistsButDraftReturns404() throws Exception {
        addProjected(PublicContentTestItems.draft("draft"), "/tr/articles/draft");

        assertSafeNotFound("/tr/articles/draft");
    }

    @Test
    void projectionExistsButPrivateReturns404() throws Exception {
        addProjected(PublicContentTestItems.publishedPrivate("private"), "/tr/articles/private");

        assertSafeNotFound("/tr/articles/private");
    }

    @Test
    void projectionExistsButUnpublishedReturns404() throws Exception {
        addProjected(PublicContentTestItems.unpublished("unpublished"), "/tr/articles/unpublished");

        assertSafeNotFound("/tr/articles/unpublished");
    }

    @Test
    void projectionExistsButArchivedReturns404() throws Exception {
        addProjected(PublicContentTestItems.archived("archived"), "/tr/articles/archived");

        assertSafeNotFound("/tr/articles/archived");
    }

    @Test
    void projectionExistsButMissingSnapshotReturns404() throws Exception {
        addProjected(PublicContentTestItems.publishedWithoutSnapshot("without-snapshot"), "/tr/articles/without-snapshot");

        assertSafeNotFound("/tr/articles/without-snapshot");
    }

    @Test
    void missingContentReturnsSameSafeNotFound() throws Exception {
        assertSafeNotFound("/tr/articles/missing");
    }

    @Test
    void invalidRouteVariablesReturnSameSafeNotFound() throws Exception {
        assertSafeNotFound("/de/articles/slug");
        assertSafeNotFound("/tr/essays/slug");
        assertSafeNotFound("/tr/articles/Invalid-Slug");
    }
}
