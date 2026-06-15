package dev.persefonia.app.exposure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.InMemoryPublicRouteResolver;
import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
import dev.persefonia.app.webpublic.content.PublicContentTestRepository;
import dev.persefonia.app.webpublic.content.PublicContentTestItems;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(PublicContentTestConfiguration.class)
@ActiveProfiles({"test", "public-content-mvc-test"})
class PublicContentExposureRegressionTest {
    @Autowired MockMvc mockMvc;
    @Autowired PublicContentTestRepository items;
    @Autowired InMemoryPublicRouteResolver routes;

    @BeforeEach
    void reset() {
        items.reset();
        routes.clear();
    }

    @Test
    void publicDetailRouteExposesOnlyDirectlyRenderableContent() throws Exception {
        addProjected(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "public"), "/tr/articles/public");
        addProjected(PublicContentTestItems.publishedUnlisted("unlisted"), "/tr/articles/unlisted");
        addProjected(PublicContentTestItems.publishedPrivate("private"), "/tr/articles/private");
        addProjected(PublicContentTestItems.draft("draft"), "/tr/articles/draft");
        addProjected(PublicContentTestItems.unpublished("unpublished"), "/tr/articles/unpublished");
        addProjected(PublicContentTestItems.archived("archived"), "/tr/articles/archived");

        mockMvc.perform(get("/tr/articles/public"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", Matchers.allOf(
                        Matchers.containsString("public"),
                        Matchers.containsString("max-age=60"))))
                .andExpect(content().string(Matchers.containsString("Persisted HTML")))
                .andExpect(content().string(Matchers.containsString("<link rel=\"canonical\" href=\"https://0xmillennium.dev/tr/articles/public\">")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("noindex"))));
        mockMvc.perform(get("/tr/articles/unlisted"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", Matchers.allOf(
                        Matchers.containsString("public"),
                        Matchers.containsString("max-age=60"))))
                .andExpect(content().string(Matchers.containsString("Persisted HTML")))
                .andExpect(content().string(Matchers.containsString("<link rel=\"canonical\" href=\"https://0xmillennium.dev/tr/articles/unlisted\">")))
                .andExpect(content().string(Matchers.containsString("<meta name=\"robots\" content=\"noindex\">")));

        assertNotFound("/tr/articles/private");
        assertNotFound("/tr/articles/draft");
        assertNotFound("/tr/articles/unpublished");
        assertNotFound("/tr/articles/archived");
        assertNotFound("/tr/articles/missing");
        assertNotFound("/tr/articles/Invalid-Slug");

        assertNotPublicContent("/de/articles/public");
        assertNotPublicContent("/tr/essays/public");
    }

    @Test
    void adminAndDiscoveryStyleRoutesRemainUnavailableAsPublicContentRoutes() throws Exception {
        String id = java.util.UUID.randomUUID().toString();

        mockMvc.perform(get("/admin/content/" + id + "/preview")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/content/" + id + "/revisions")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/sitemap.xml")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/feed")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/search")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/tags/topic")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/tr")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/en")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/articles")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/tr/articles")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/en/articles")).andExpect(status().is4xxClientError());
    }

    private void assertNotFound(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", Matchers.allOf(
                        Matchers.containsString("no-store"),
                        Matchers.containsString("private"))))
                .andExpect(content().string(Matchers.containsString("The page you requested was not found.")))
                .andExpect(content().string(Matchers.containsString("<meta name=\"robots\" content=\"noindex\">")));
    }

    private void assertNotPublicContent(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is4xxClientError())
                .andExpect(header().string("Cache-Control", Matchers.not(Matchers.containsString("public"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Persisted HTML"))));
    }

    private void addProjected(ContentItem item, String publicPath) {
        items.add(item);
        routes.addFound(publicPath, item.id().value());
    }
}
