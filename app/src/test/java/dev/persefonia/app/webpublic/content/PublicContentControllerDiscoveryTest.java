package dev.persefonia.app.webpublic.content;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import org.junit.jupiter.api.Test;

class PublicContentControllerDiscoveryTest extends PublicContentMvcTestSupport {
    @Test
    void rendersPublishedPublicContentForSupportedRoutes() throws Exception {
        addProjected(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "makale"), "/tr/articles/makale");
        addProjected(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.EN, "articles", "article"), "/en/articles/article");
        addProjected(PublicContentTestItems.publishedPublic(
                ContentType.NOTE, ContentLanguage.TR, "notes", "not"), "/tr/notes/not");
        addProjected(PublicContentTestItems.publishedPublic(
                ContentType.RESEARCH, ContentLanguage.TR, "research", "arastirma"), "/tr/research/arastirma");
        addProjected(PublicContentTestItems.publishedPublic(
                ContentType.PAGE, ContentLanguage.TR, "pages", "hakkinda"), "/tr/pages/hakkinda");

        assertRendered("/tr/articles/makale");
        assertRendered("/en/articles/article");
        assertRendered("/tr/notes/not");
        assertRendered("/tr/research/arastirma");
        assertRendered("/tr/pages/hakkinda");
    }

    @Test
    void unlistedProjectionAndContentReturns200Noindex() throws Exception {
        addProjected(PublicContentTestItems.publishedUnlisted("unlisted"), "/tr/articles/unlisted");

        mockMvc.perform(get("/tr/articles/unlisted"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(containsString("Persisted HTML")));
    }

    @Test
    void publicPresentationIncludesMetadataTocAndConditionalMermaidLoader() throws Exception {
        addProjected(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "metadata"), "/tr/articles/metadata");
        addProjected(PublicContentTestItems.publishedPublicWithMermaid("diagram"), "/tr/articles/diagram");

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
}
