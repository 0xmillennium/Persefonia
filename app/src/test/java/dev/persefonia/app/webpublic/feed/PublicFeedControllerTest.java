package dev.persefonia.app.webpublic.feed;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
import dev.persefonia.app.webpublic.feed.PublicFeedTestConfiguration.StubPublicFeedIndexQueryService;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.index.PublicFeedEntry;
import java.time.Instant;
import java.util.List;
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
@Import({PublicContentTestConfiguration.class, PublicFeedTestConfiguration.class})
@ActiveProfiles({"test", "public-content-mvc-test", "public-feed-mvc-test"})
class PublicFeedControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired StubPublicFeedIndexQueryService feedIndex;

    @BeforeEach
    void reset() {
        feedIndex.reset();
    }

    @Test
    void feedIsPublicAtomWithNosniffAndFeedCache() throws Exception {
        mockMvc.perform(get("/feed.xml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/atom+xml;charset=UTF-8"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "public, no-cache, must-revalidate"))
                .andExpect(header().string("Cache-Control", not(containsString("immutable"))))
                .andExpect(content().string(containsString(
                        "<feed xmlns=\"http://www.w3.org/2005/Atom\">")));
    }

    @Test
    void feedRendersConfiguredAbsoluteUrlsAndEligibleEntries() throws Exception {
        mockMvc.perform(get("/feed.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<id>https://0xmillennium.dev/feed.xml</id>")))
                .andExpect(content().string(containsString(
                        "<link rel=\"self\" href=\"https://0xmillennium.dev/feed.xml\"/>")))
                .andExpect(content().string(containsString(
                        "<link rel=\"alternate\" href=\"https://0xmillennium.dev/\"/>")))
                .andExpect(content().string(containsString(
                        "<id>https://0xmillennium.dev/en/articles/published-article</id>")))
                .andExpect(content().string(containsString(
                        "<id>https://0xmillennium.dev/tr/notes/published-note</id>")))
                .andExpect(content().string(containsString(
                        "<id>https://0xmillennium.dev/en/research/published-research</id>")))
                .andExpect(content().string(containsString("<summary>An eligible article summary</summary>")))
                // Summary-only: no full content, enclosures, or RSS elements.
                .andExpect(content().string(not(containsString("<content"))))
                .andExpect(content().string(not(containsString("enclosure"))))
                .andExpect(content().string(not(containsString("<rss"))))
                // Projects are never returned by the feed query contract.
                .andExpect(content().string(not(containsString("/projects"))));
    }

    @Test
    void configuredBaseUrlIsUsedRegardlessOfHostHeaders() throws Exception {
        mockMvc.perform(get("/feed.xml")
                        .header("Host", "evil.example")
                        .header("X-Forwarded-Host", "attacker.test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://0xmillennium.dev/feed.xml")))
                .andExpect(content().string(not(containsString("evil.example"))))
                .andExpect(content().string(not(containsString("attacker.test"))));
    }

    @Test
    void feedExcludesUnsafeRouteFamiliesEvenFromSource() throws Exception {
        feedIndex.entries(List.of(
                stub("/search", "Search"),
                stub("/contact", "Contact"),
                stub("/sitemap.xml", "Sitemap"),
                stub("/robots.txt", "Robots"),
                stub("/feed.xml", "Feed"),
                stub("/cv", "Cv"),
                stub("/cv/download", "CvDownload"),
                stub("/media/assets/1/variants/large", "Media"),
                stub("/tr/tags/topic", "Tag"),
                stub("/en/series/series-x", "Series"),
                stub("/en/articles/keep", "Keep")));

        mockMvc.perform(get("/feed.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/search"))))
                .andExpect(content().string(not(containsString("/contact"))))
                .andExpect(content().string(not(containsString("/sitemap.xml"))))
                .andExpect(content().string(not(containsString("/robots.txt"))))
                .andExpect(content().string(not(containsString("/cv"))))
                .andExpect(content().string(not(containsString("/media"))))
                .andExpect(content().string(not(containsString("/tags/"))))
                .andExpect(content().string(not(containsString("/series/"))))
                .andExpect(content().string(containsString(
                        "<id>https://0xmillennium.dev/en/articles/keep</id>")));
    }

    @Test
    void postAndWildcardFeedRoutesAreNotOpened() throws Exception {
        mockMvc.perform(post("/feed.xml")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/feed.xml/anything")).andExpect(status().is4xxClientError());
    }

    private static PublicFeedEntry stub(String publicUrl, String title) {
        return new PublicFeedEntry(
                "ARTICLE",
                "id-" + Math.abs(publicUrl.hashCode()),
                DiscoveryLanguage.EN,
                publicUrl,
                "https://0xmillennium.dev" + publicUrl,
                title,
                "summary",
                Instant.parse("2026-06-24T12:00:00Z"),
                Instant.parse("2026-06-24T12:00:00Z"));
    }
}
