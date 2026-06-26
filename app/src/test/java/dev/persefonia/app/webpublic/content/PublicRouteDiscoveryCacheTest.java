package dev.persefonia.app.webpublic.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class PublicRouteDiscoveryCacheTest extends PublicContentMvcTestSupport {
    @Test
    void foundContentUsesPublicCacheOnlyWhenDiscoveryProjectionExists() throws Exception {
        items.add(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "no-projection-cache"));
        assertNoStorePrivate(mockMvc.perform(get("/tr/articles/no-projection-cache"))
                .andExpect(status().isNotFound())
                .andReturn());

        addProjected(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "projected-cache"), "/tr/articles/projected-cache");
        assertPublicCache(mockMvc.perform(get("/tr/articles/projected-cache"))
                .andExpect(status().isOk())
                .andReturn());
    }

    @Test
    void discoveryRedirectUsesPublicCacheWithoutContentState() throws Exception {
        routes.addRedirect(
                "/tr/articles/old-cache",
                "/tr/articles/new-cache",
                RedirectStatusCode.PERMANENT_REDIRECT_308);

        assertPublicCache(mockMvc.perform(get("/tr/articles/old-cache"))
                .andExpect(status().isPermanentRedirect())
                .andReturn());
    }

    private static void assertPublicCache(MvcResult result) {
        assertThat(cacheControlTokens(result)).contains("public", "max-age=300");
    }

    private static void assertNoStorePrivate(MvcResult result) {
        assertThat(cacheControlTokens(result)).contains("no-store", "private");
    }

    private static String[] cacheControlTokens(MvcResult result) {
        return Arrays.stream(result.getResponse().getHeader("Cache-Control").split(","))
                .map(value -> value.trim())
                .toArray(String[]::new);
    }
}
