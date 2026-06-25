package dev.persefonia.app.webpublic.sitemap;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
import dev.persefonia.app.webpublic.sitemap.PublicSitemapTestConfiguration.StubPublicSitemapIndexQueryService;
import dev.persefonia.app.webpublic.sitemap.PublicSitemapTestConfiguration.ToggleablePublicCvAvailability;
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
@Import({PublicContentTestConfiguration.class, PublicSitemapTestConfiguration.class})
@ActiveProfiles({"test", "public-content-mvc-test", "public-sitemap-mvc-test"})
class PublicSitemapControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired StubPublicSitemapIndexQueryService sitemapIndex;
    @Autowired ToggleablePublicCvAvailability cvAvailability;

    @BeforeEach
    void reset() {
        sitemapIndex.reset();
        cvAvailability.present(true);
    }

    @Test
    void sitemapIsPublicXmlWithNosniffAndPublicCache() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml;charset=UTF-8"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "public, max-age=3600, must-revalidate"))
                .andExpect(header().string("Cache-Control", not(containsString("immutable"))))
                .andExpect(content().string(containsString(
                        "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")));
    }

    @Test
    void sitemapCombinesStaticAndDynamicAbsoluteUrls() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<loc>https://0xmillennium.dev/</loc>")))
                .andExpect(content().string(containsString("<loc>https://0xmillennium.dev/en/projects</loc>")))
                .andExpect(content().string(containsString("<loc>https://0xmillennium.dev/tr/projects</loc>")))
                .andExpect(content().string(containsString("<loc>https://0xmillennium.dev/cv</loc>")))
                .andExpect(content().string(containsString(
                        "<loc>https://0xmillennium.dev/en/projects/portfolio</loc>")))
                .andExpect(content().string(containsString("<lastmod>2026-06-24</lastmod>")));
    }

    @Test
    void sitemapExcludesCvPageWhenNoActiveCvExists() throws Exception {
        cvAvailability.present(false);

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<loc>https://0xmillennium.dev/cv</loc>"))));
    }

    @Test
    void sitemapExcludesSearchCvDownloadMediaAndAdminEvenFromDynamicSource() throws Exception {
        sitemapIndex.entries(List.of(
                new dev.persefonia.discovery.application.index.PublicSitemapEntry(
                        "/search/results",
                        "https://0xmillennium.dev/search/results",
                        dev.persefonia.discovery.application.contract.DiscoveryLanguage.EN,
                        java.time.Instant.parse("2026-06-24T12:00:00Z")),
                new dev.persefonia.discovery.application.index.PublicSitemapEntry(
                        "/media/assets/1/variants/large",
                        "https://0xmillennium.dev/media/assets/1/variants/large",
                        dev.persefonia.discovery.application.contract.DiscoveryLanguage.EN,
                        java.time.Instant.parse("2026-06-24T12:00:00Z"))));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/search"))))
                .andExpect(content().string(not(containsString("/media/assets"))))
                .andExpect(content().string(not(containsString("/cv/download"))))
                .andExpect(content().string(not(containsString("/admin"))))
                .andExpect(content().string(not(containsString("priority"))))
                .andExpect(content().string(not(containsString("changefreq"))))
                .andExpect(content().string(not(containsString("hreflang"))));
    }

    @Test
    void configuredBaseUrlIsUsedRegardlessOfHostHeaders() throws Exception {
        mockMvc.perform(get("/sitemap.xml")
                        .header("Host", "evil.example")
                        .header("X-Forwarded-Host", "attacker.test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<loc>https://0xmillennium.dev/</loc>")))
                .andExpect(content().string(not(containsString("evil.example"))))
                .andExpect(content().string(not(containsString("attacker.test"))));
    }

    @Test
    void postAndWildcardSitemapRoutesAreNotOpened() throws Exception {
        mockMvc.perform(post("/sitemap.xml")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/sitemap.xml/anything")).andExpect(status().is4xxClientError());
    }
}
