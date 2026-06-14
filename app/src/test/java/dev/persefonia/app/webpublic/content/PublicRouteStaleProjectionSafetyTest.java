package dev.persefonia.app.webpublic.content;

import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import org.junit.jupiter.api.Test;

class PublicRouteStaleProjectionSafetyTest extends PublicContentMvcTestSupport {
    @Test
    void projectionExpectedPathMismatchReturns404() throws Exception {
        ContentItem item = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "current-slug");
        items.add(item);
        routes.addFound("/tr/articles/old-slug", item.id().value());

        assertSafeNotFound("/tr/articles/old-slug");
    }
}
