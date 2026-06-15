package dev.persefonia.app.webpublic.content;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import org.junit.jupiter.api.Test;

class PublicContentDiscoveryRouteResolutionTest extends PublicContentMvcTestSupport {
    @Test
    void publicContentRequiresCurrentDiscoveryProjection() throws Exception {
        ContentItem item = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "closure-current");
        items.add(item);

        assertSafeNotFound("/tr/articles/closure-current");

        routes.addFound("/tr/articles/closure-old", item.id().value());

        assertSafeNotFound("/tr/articles/closure-old");
    }

    @Test
    void unlistedDirectUrlRemainsRenderableThroughDiscoveryProjection() throws Exception {
        addProjected(PublicContentTestItems.publishedUnlisted("closure-unlisted"), "/tr/articles/closure-unlisted");

        mockMvc.perform(get("/tr/articles/closure-unlisted"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(containsString("Persisted HTML")));
    }
}
