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
class PublicContentControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired PublicContentTestRepository items;

    @BeforeEach
    void reset() {
        items.reset();
    }

    @Test
    void rendersPublishedPublicContentForSupportedRoutes() throws Exception {
        items.add(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "makale"));
        items.add(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.EN, "articles", "article"));
        items.add(PublicContentTestItems.publishedPublic(
                ContentType.NOTE, ContentLanguage.TR, "notes", "not"));
        items.add(PublicContentTestItems.publishedPublic(
                ContentType.RESEARCH, ContentLanguage.TR, "research", "arastirma"));
        items.add(PublicContentTestItems.publishedPublic(
                ContentType.PAGE, ContentLanguage.TR, "pages", "hakkinda"));

        assertRendered("/tr/articles/makale");
        assertRendered("/en/articles/article");
        assertRendered("/tr/notes/not");
        assertRendered("/tr/research/arastirma");
        assertRendered("/tr/pages/hakkinda");
    }

    @Test
    void publishedUnlistedContentRendersByDirectUrl() throws Exception {
        items.add(PublicContentTestItems.publishedUnlisted("unlisted"));

        mockMvc.perform(get("/tr/articles/unlisted"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(containsString("Persisted HTML")));
    }

    @Test
    void publicPresentationIncludesMetadataTocAndConditionalMermaidLoader() throws Exception {
        items.add(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "metadata"));
        items.add(PublicContentTestItems.publishedPublicWithMermaid("diagram"));

        mockMvc.perform(get("/tr/articles/metadata").header("Host", "attacker.example"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"https://0xmillennium.dev/tr/articles/metadata\">")))
                .andExpect(content().string(containsString("<meta property=\"og:url\" content=\"https://0xmillennium.dev/tr/articles/metadata\">")))
                .andExpect(content().string(containsString("<meta property=\"og:type\" content=\"article\">")))
                .andExpect(content().string(containsString("<a href=\"#heading-escaped\">Heading &lt;Escaped&gt;</a>")))
                .andExpect(content().string(not(containsString("attacker.example"))))
                .andExpect(content().string(not(containsString("noindex"))))
                .andExpect(content().string(not(containsString("mermaid-loader-test.js"))));

        mockMvc.perform(get("/tr/articles/diagram"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("mermaid-loader-test.js")));
    }

    @Test
    void nonPublicAndMissingContentReturnSameSafeNotFound() throws Exception {
        items.add(PublicContentTestItems.draft("draft"));
        items.add(PublicContentTestItems.unpublished("unpublished"));
        items.add(PublicContentTestItems.archived("archived"));
        items.add(PublicContentTestItems.publishedPrivate("private"));
        items.add(PublicContentTestItems.publishedWithoutSnapshot("without-snapshot"));

        assertSafeNotFound("/tr/articles/draft");
        assertSafeNotFound("/tr/articles/unpublished");
        assertSafeNotFound("/tr/articles/archived");
        assertSafeNotFound("/tr/articles/private");
        assertSafeNotFound("/tr/articles/without-snapshot");
        assertSafeNotFound("/tr/articles/missing");
    }

    @Test
    void invalidRouteVariablesReturnSameSafeNotFound() throws Exception {
        assertSafeNotFound("/de/articles/slug");
        assertSafeNotFound("/tr/essays/slug");
        assertSafeNotFound("/tr/articles/Invalid-Slug");
    }

    private void assertRendered(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Public &lt;Title&gt;")))
                .andExpect(content().string(containsString("Summary &lt;strong&gt;escaped&lt;/strong&gt;")))
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"https://0xmillennium.dev")))
                .andExpect(content().string(containsString("<strong>Persisted HTML</strong>")))
                .andExpect(content().string(containsString("4 min read")))
                .andExpect(content().string(containsString("Heading &lt;Escaped&gt;")))
                .andExpect(content().string(not(containsString("noindex"))))
                .andExpect(content().string(not(containsString("markdownSource"))))
                .andExpect(content().string(not(containsString("/admin/content"))))
                .andExpect(content().string(not(containsString("/preview"))))
                .andExpect(content().string(not(containsString("/revisions"))));
    }

    private void assertSafeNotFound(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("The page you requested was not found.")))
                .andExpect(content().string(containsString("noindex")))
                .andExpect(content().string(not(containsString("private"))))
                .andExpect(content().string(not(containsString("draft"))))
                .andExpect(content().string(not(containsString("unpublished"))))
                .andExpect(content().string(not(containsString("archived"))));
    }
}
