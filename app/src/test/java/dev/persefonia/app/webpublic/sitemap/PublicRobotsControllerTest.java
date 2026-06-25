package dev.persefonia.app.webpublic.sitemap;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
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
class PublicRobotsControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void robotsIsPublicPlainTextWithNosniffAndPublicCache() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "public, max-age=3600, must-revalidate"))
                .andExpect(header().string("Cache-Control", not(containsString("immutable"))));
    }

    @Test
    void robotsContainsRequiredDirectivesAndAbsoluteSitemap() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("User-agent: *")))
                .andExpect(content().string(containsString("Disallow: /admin")))
                .andExpect(content().string(containsString("Disallow: /actuator")))
                .andExpect(content().string(containsString("Disallow: /oauth2")))
                .andExpect(content().string(containsString("Disallow: /login")))
                .andExpect(content().string(containsString("Disallow: /logout")))
                .andExpect(content().string(containsString("Disallow: /preview")))
                .andExpect(content().string(containsString("Disallow: /search")))
                .andExpect(content().string(containsString("Disallow: /cv/download")))
                .andExpect(content().string(containsString("Disallow: /cv/*/download")))
                .andExpect(content().string(containsString("Sitemap: https://0xmillennium.dev/sitemap.xml")));
    }

    @Test
    void robotsUrlIgnoresHostHeaders() throws Exception {
        mockMvc.perform(get("/robots.txt")
                        .header("Host", "evil.example")
                        .header("X-Forwarded-Host", "attacker.test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sitemap: https://0xmillennium.dev/sitemap.xml")))
                .andExpect(content().string(not(containsString("evil.example"))))
                .andExpect(content().string(not(containsString("attacker.test"))));
    }

    @Test
    void postAndWildcardRobotsRoutesAreNotOpened() throws Exception {
        mockMvc.perform(post("/robots.txt")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/robots.txt/anything")).andExpect(status().is4xxClientError());
    }
}
