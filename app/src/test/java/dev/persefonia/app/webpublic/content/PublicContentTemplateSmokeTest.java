package dev.persefonia.app.webpublic.content;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(PublicContentTestConfiguration.class)
@ActiveProfiles({"test", "public-content-mvc-test"})
class PublicContentTemplateSmokeTest {
    @Autowired MockMvc mockMvc;
    @Autowired PublicContentTestRepository items;

    @BeforeEach
    void reset() {
        items.reset();
    }

    @Test
    void contentTemplateRendersSnapshotAndEscapesPublicFields() throws Exception {
        items.add(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "template-smoke"));

        mockMvc.perform(get("/tr/articles/template-smoke"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Public &lt;Title&gt;")))
                .andExpect(content().string(containsString("Summary &lt;strong&gt;escaped&lt;/strong&gt;")))
                .andExpect(content().string(containsString("2026-06-12T12:00:01Z")))
                .andExpect(content().string(containsString("4 min read")))
                .andExpect(content().string(containsString("<p><strong>Persisted HTML</strong></p>")))
                .andExpect(content().string(containsString("Heading &lt;Escaped&gt;")))
                .andExpect(content().string(not(containsString("markdownSource"))))
                .andExpect(content().string(not(containsString("/admin/content"))))
                .andExpect(content().string(not(containsString("/preview"))))
                .andExpect(content().string(not(containsString("/revisions"))));
    }

    @Test
    void notFoundTemplateRendersGenericNoindexMessage() throws Exception {
        mockMvc.perform(get("/tr/articles/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("The page you requested was not found.")))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(not(containsString("draft"))))
                .andExpect(content().string(not(containsString("private"))))
                .andExpect(content().string(not(containsString("/admin/content"))));
    }
}
