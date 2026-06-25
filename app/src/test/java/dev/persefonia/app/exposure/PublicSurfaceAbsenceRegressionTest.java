package dev.persefonia.app.exposure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class PublicSurfaceAbsenceRegressionTest {
    @Autowired MockMvc mockMvc;

    @Test
    void publicReadingAndDiscoveryStyleRoutesRemainAbsent() throws Exception {
        for (String path : List.of(
                "/content/draft",
                "/content/published",
                "/content/unpublished",
                "/content/archived",
                "/content/private",
                "/articles/published",
                "/content",
                "/series",
                "/en/series",
                "/tr/series",
                "/feed",
                "/feed.xml/anything",
                "/rss.xml",
                "/atom.xml",
                "/sitemap.xml/anything",
                "/robots.txt/anything",
                "/search/anything")) {
            mockMvc.perform(get(path)).andExpect(status().is4xxClientError());
        }

        mockMvc.perform(get("/search")).andExpect(status().isOk());
        mockMvc.perform(get("/robots.txt")).andExpect(status().isOk());
    }

    @Test
    void adminPreviewAndRevisionRoutesAreNotPubliclyAccessible() throws Exception {
        String id = java.util.UUID.randomUUID().toString();
        mockMvc.perform(get("/admin/content/" + id + "/preview")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/content/" + id + "/revisions")).andExpect(status().is4xxClientError());
    }
}
