package dev.persefonia.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.web.PublicHomeTestConfiguration.StubHomepageContentQueryService;
import dev.persefonia.discovery.application.index.PublicHomepageContentEntry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
@Import(PublicHomeTestConfiguration.class)
@ActiveProfiles({"test", "public-home-mvc-test"})
class PublicHomeChaptersTest {
    @Autowired MockMvc mockMvc;
    @Autowired StubHomepageContentQueryService content;

    @AfterEach
    void reset() {
        content.setContent(List.of(), List.of());
    }

    @Test
    void homepageRendersCurrentScenesAndPublicContentWithoutObsoleteExperiments() throws Exception {
        content.setContent(
                List.of(new PublicHomepageContentEntry(
                        "A real article", "Published summary", "/en/articles/real-article"),
                        new PublicHomepageContentEntry("A real note", "Note summary", "/en/notes/real-note")),
                List.of(new PublicHomepageContentEntry(
                        "A real study", "Research summary", "/en/research/real-study")));

        String html = mockMvc.perform(get("/")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String id : List.of("identity", "work", "writing", "contact")) {
            assertThat(html).contains("id=\"" + id + "\"");
        }
        assertThat(html).contains("<h1 id=\"identity-title\" class=\"home-masthead\">0xmillennium");
        assertThat(html).contains("href=\"#work\"", "href=\"#writing\"", "href=\"/contact\"");
        assertThat(html).contains("href=\"/en/articles/real-article\"");
        assertThat(html).contains("A real article");
        assertThat(html).contains("A real study", "A real note", "data-category=\"articles\"", "data-category=\"notes\"", "data-category=\"research\"");
        assertThat(html).doesNotContain("id=\"architecture\"", "id=\"about\"",
                "href=\"#about\"", "href=\"#architecture\"", "class=\"film-controls\"",
                "data-film-switch", "data-film-retry", "id=\"find-location\"", "id=\"tilt-up\"", "id=\"pause\"");
        assertThat(html).contains("class=\"home-cinema__image\" aria-hidden=\"true\"");
        assertThat(html).doesNotContain("[Published article title]", "[Published research title]", "ref. pending");
    }
}
