package dev.persefonia.app.webpublic.content;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
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
    @Autowired InMemoryPublicRouteResolver routes;

    @BeforeEach
    void reset() {
        items.reset();
        routes.clear();
    }

    @Test
    void contentTemplateRendersSnapshotAndEscapesPublicFields() throws Exception {
        addProjected(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "template-smoke"), "/tr/articles/template-smoke");

        mockMvc.perform(get("/tr/articles/template-smoke"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>SEO &lt;Title&gt;</title>")))
                .andExpect(content().string(containsString("<meta name=\"description\" content=\"SEO &lt;description>\">")))
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"https://0xmillennium.dev/tr/articles/template-smoke\">")))
                .andExpect(content().string(containsString("<meta property=\"og:title\" content=\"OG &lt;Title>\">")))
                .andExpect(content().string(containsString("<meta property=\"og:description\" content=\"OG &lt;description>\">")))
                .andExpect(content().string(containsString("<meta property=\"og:url\" content=\"https://0xmillennium.dev/tr/articles/template-smoke\">")))
                .andExpect(content().string(containsString("<meta property=\"og:type\" content=\"article\">")))
                .andExpect(content().string(containsString("<meta property=\"article:published_time\" content=\"2026-06-12T12:00:01Z\">")))
                .andExpect(content().string(containsString("<meta property=\"article:modified_time\" content=\"2026-06-12T12:00:01Z\">")))
                .andExpect(content().string(containsString("Public &lt;Title&gt;")))
                .andExpect(content().string(containsString("Summary &lt;strong&gt;escaped&lt;/strong&gt;")))
                .andExpect(content().string(containsString("<time datetime=\"2026-06-12T12:00:01Z\">2026-06-12</time>")))
                .andExpect(content().string(containsString("4 min read")))
                .andExpect(content().string(containsString("<nav class=\"public-toc\" aria-label=\"Table of contents\">")))
                .andExpect(content().string(containsString("<a href=\"#heading-escaped\">Heading &lt;Escaped&gt;</a>")))
                .andExpect(content().string(containsString("<h2 id=\"heading-escaped\">Persisted heading</h2>")))
                .andExpect(content().string(containsString("<p><strong>Persisted HTML</strong></p>")))
                .andExpect(content().string(containsString("Heading &lt;Escaped&gt;")))
                .andExpect(content().string(not(containsString("<meta name=\"robots\" content=\"noindex\">"))))
                .andExpect(content().string(not(containsString("mermaid-loader-test.js"))))
                .andExpect(content().string(not(containsString("markdownSource"))))
                .andExpect(content().string(not(containsString("/admin/content"))))
                .andExpect(content().string(not(containsString("/preview"))))
                .andExpect(content().string(not(containsString("/revisions"))));
    }

    @Test
    void contentTemplateOmitsTocWhenHeadingsAreEmptyAndLoadsMermaidConditionally() throws Exception {
        addProjected(PublicContentTestItems.publishedPublicWithoutHeadings("without-headings"), "/tr/articles/without-headings");
        addProjected(PublicContentTestItems.publishedPublicWithMermaid("with-mermaid"), "/tr/articles/with-mermaid");

        mockMvc.perform(get("/tr/articles/without-headings"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Table of contents"))))
                .andExpect(content().string(not(containsString("mermaid-loader-test.js"))));

        mockMvc.perform(get("/tr/articles/with-mermaid"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<script type=\"module\" src=\"/assets/mermaid-loader-test.js\"></script>")))
                .andExpect(content().string(containsString("language-mermaid")));
    }

    @Test
    void unlistedContentRendersNoindex() throws Exception {
        addProjected(PublicContentTestItems.publishedUnlisted("template-unlisted"), "/tr/articles/template-unlisted");

        mockMvc.perform(get("/tr/articles/template-unlisted"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")));
    }

    @Test
    void notFoundTemplateRendersGenericNoindexMessage() throws Exception {
        mockMvc.perform(get("/tr/articles/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("The page you requested was not found.")))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(containsString("<link rel=\"stylesheet\" href=\"/assets/main-test.css\">")))
                .andExpect(content().string(not(containsString("draft"))))
                .andExpect(content().string(not(containsString("private"))))
                .andExpect(content().string(not(containsString("/admin/content"))));
    }

    private void addProjected(ContentItem item, String publicPath) {
        items.add(item);
        routes.addFound(publicPath, item.id().value());
    }
}
