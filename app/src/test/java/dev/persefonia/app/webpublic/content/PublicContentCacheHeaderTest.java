package dev.persefonia.app.webpublic.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
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

    @BeforeEach
    void reset() {
        items.reset();
    }

    @Test
    void publishedPublicContentUsesShortPublicCache() throws Exception {
        items.add(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "public-cache"));

        assertPublicHtmlCache(mockMvc.perform(get("/tr/articles/public-cache"))
                .andExpect(status().isOk())
                .andReturn());
    }

    @Test
    void publishedUnlistedContentUsesShortPublicCache() throws Exception {
        items.add(PublicContentTestItems.publishedUnlisted("unlisted-cache"));

        assertPublicHtmlCache(mockMvc.perform(get("/tr/articles/unlisted-cache"))
                .andExpect(status().isOk())
                .andReturn());
    }

    @Test
    void nonPublicContentUsesNoStorePrivateCache() throws Exception {
        items.add(PublicContentTestItems.draft("draft-cache"));
        items.add(PublicContentTestItems.unpublished("unpublished-cache"));
        items.add(PublicContentTestItems.archived("archived-cache"));
        items.add(PublicContentTestItems.publishedPrivate("private-cache"));
        items.add(PublicContentTestItems.publishedWithoutSnapshot("without-snapshot-cache"));

        assertNoStorePrivate("/tr/articles/draft-cache");
        assertNoStorePrivate("/tr/articles/unpublished-cache");
        assertNoStorePrivate("/tr/articles/archived-cache");
        assertNoStorePrivate("/tr/articles/private-cache");
        assertNoStorePrivate("/tr/articles/without-snapshot-cache");
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
        assertThat(cacheControlTokens(result)).contains("public", "max-age=60");
    }

    private static void assertNoStorePrivate(MvcResult result) {
        assertThat(cacheControlTokens(result)).contains("no-store", "private");
    }

    private static String[] cacheControlTokens(MvcResult result) {
        String cacheControl = result.getResponse().getHeader("Cache-Control");
        assertThat(cacheControl).isNotBlank();
        return java.util.Arrays.stream(cacheControl.split(","))
                .map(String::trim)
                .toArray(String[]::new);
    }
}
