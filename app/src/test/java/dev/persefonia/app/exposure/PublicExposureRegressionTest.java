package dev.persefonia.app.exposure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
import dev.persefonia.app.webpublic.content.PublicContentTestRepository;
import dev.persefonia.app.webpublic.content.PublicContentTestItems;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
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
class PublicExposureRegressionTest {
    @Autowired MockMvc mockMvc;
    @Autowired PublicContentTestRepository items;

    @BeforeEach
    void reset() {
        items.reset();
    }

    @Test
    void publicDetailRouteExposesOnlyDirectlyRenderableContent() throws Exception {
        items.add(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "public"));
        items.add(PublicContentTestItems.publishedUnlisted("unlisted"));
        items.add(PublicContentTestItems.publishedPrivate("private"));
        items.add(PublicContentTestItems.draft("draft"));
        items.add(PublicContentTestItems.unpublished("unpublished"));
        items.add(PublicContentTestItems.archived("archived"));

        mockMvc.perform(get("/tr/articles/public"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Persisted HTML")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("noindex"))));
        mockMvc.perform(get("/tr/articles/unlisted"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Persisted HTML")))
                .andExpect(content().string(Matchers.containsString("<meta name=\"robots\" content=\"noindex\">")));

        assertNotFound("/tr/articles/private");
        assertNotFound("/tr/articles/draft");
        assertNotFound("/tr/articles/unpublished");
        assertNotFound("/tr/articles/archived");
        assertNotFound("/tr/articles/missing");
        assertNotFound("/de/articles/public");
        assertNotFound("/tr/essays/public");
        assertNotFound("/tr/articles/Invalid-Slug");
    }

    @Test
    void adminAndDiscoveryStyleRoutesRemainUnavailableAsPublicContentRoutes() throws Exception {
        String id = java.util.UUID.randomUUID().toString();

        mockMvc.perform(get("/admin/content/" + id + "/preview")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/content/" + id + "/revisions")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/sitemap.xml")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/feed")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/search")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/tr/articles")).andExpect(status().is4xxClientError());
    }

    private void assertNotFound(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(content().string(Matchers.containsString("The page you requested was not found.")))
                .andExpect(content().string(Matchers.containsString("<meta name=\"robots\" content=\"noindex\">")));
    }
}
