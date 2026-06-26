package dev.persefonia.app.webpublic.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
@Import(PublicContentTestConfiguration.class)
@ActiveProfiles({"test", "public-content-mvc-test"})
class PublicContentCacheHeaderTest {
    @Autowired MockMvc mockMvc;
    @Autowired PublicContentTestRepository items;
    @Autowired InMemoryPublicRouteResolver routes;

    @BeforeEach
    void reset() {
        items.reset();
        routes.clear();
    }

    @Test
    void publishedPublicContentUsesShortPublicCache() throws Exception {
        addProjected(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "public-cache"), "/tr/articles/public-cache");

        assertPublicHtmlCache(mockMvc.perform(get("/tr/articles/public-cache"))
                .andExpect(status().isOk())
                .andReturn());
    }

    @Test
    void publishedUnlistedContentUsesShortPublicCache() throws Exception {
        addProjected(PublicContentTestItems.publishedUnlisted("unlisted-cache"), "/tr/articles/unlisted-cache");

        assertPublicHtmlCache(mockMvc.perform(get("/tr/articles/unlisted-cache"))
                .andExpect(status().isOk())
                .andReturn());
    }

    @Test
    void nonPublicContentUsesNoStorePrivateCache() throws Exception {
        addProjected(PublicContentTestItems.draft("draft-cache"), "/tr/articles/draft-cache");
        addProjected(PublicContentTestItems.unpublished("unpublished-cache"), "/tr/articles/unpublished-cache");
        addProjected(PublicContentTestItems.archived("archived-cache"), "/tr/articles/archived-cache");
        addProjected(PublicContentTestItems.publishedPrivate("private-cache"), "/tr/articles/private-cache");
        addProjected(PublicContentTestItems.publishedWithoutSnapshot("without-snapshot-cache"), "/tr/articles/without-snapshot-cache");

        assertNoStorePrivate("/tr/articles/draft-cache");
        assertNoStorePrivate("/tr/articles/unpublished-cache");
        assertNoStorePrivate("/tr/articles/archived-cache");
        assertNoStorePrivate("/tr/articles/private-cache");
        assertNoStorePrivate("/tr/articles/without-snapshot-cache");
    }

    @Test
    void redirectUsesShortPublicCache() throws Exception {
        routes.addRedirect(
                "/tr/articles/old-cache",
                "/tr/articles/new-cache",
                RedirectStatusCode.PERMANENT_REDIRECT_308);

        assertPublicHtmlCache(mockMvc.perform(get("/tr/articles/old-cache"))
                .andExpect(status().isPermanentRedirect())
                .andReturn());
    }

    @Test
    void missingAndInvalidRoutesUseNoStorePrivateCache() throws Exception {
        assertNoStorePrivate("/tr/articles/missing-cache");
        assertNoStorePrivate("/de/articles/slug");
        assertNoStorePrivate("/tr/essays/slug");
        assertNoStorePrivate("/tr/articles/Invalid-Slug");
    }

    private void assertNoStorePrivate(String path) throws Exception {
        assertNoStorePrivate(mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andReturn());
    }

    private static void assertPublicHtmlCache(MvcResult result) {
        assertThat(cacheControlTokens(result)).contains("public", "max-age=300");
    }

    private static void assertNoStorePrivate(MvcResult result) {
        assertThat(cacheControlTokens(result)).contains("no-store", "private");
    }

    private void addProjected(ContentItem item, String publicPath) {
        items.add(item);
        routes.addFound(publicPath, item.id().value());
    }

    private static String[] cacheControlTokens(MvcResult result) {
        String cacheControl = result.getResponse().getHeader("Cache-Control");
        assertThat(cacheControl).isNotBlank();
        return java.util.Arrays.stream(cacheControl.split(","))
                .map(value -> value.trim())
                .toArray(String[]::new);
    }
}
