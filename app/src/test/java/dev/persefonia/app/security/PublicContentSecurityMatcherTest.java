package dev.persefonia.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.InMemoryPublicRouteResolver;
import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
import dev.persefonia.app.webpublic.content.PublicContentTestItems;
import dev.persefonia.app.webpublic.content.PublicContentTestRepository;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(PublicContentTestConfiguration.class)
@ActiveProfiles({"test", "public-content-mvc-test"})
class PublicContentSecurityMatcherTest {
    @Autowired MockMvc mockMvc;
    @Autowired PublicContentTestRepository items;
    @Autowired InMemoryPublicRouteResolver routes;

    @BeforeEach
    void reset() {
        items.reset();
        routes.clear();
    }

    @Test
    void supportedPublicContentGetRoutesArePermitted() throws Exception {
        addProjected(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "valid-public-slug"), "/tr/articles/valid-public-slug");
        addProjected(PublicContentTestItems.publishedPublic(
                ContentType.PAGE, ContentLanguage.EN, "pages", "valid-page-slug"), "/en/pages/valid-page-slug");

        mockMvc.perform(get("/tr/articles/valid-public-slug"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Persisted HTML")));
        mockMvc.perform(get("/en/pages/valid-page-slug"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Persisted HTML")));
    }

    @Test
    void unsupportedThreeSegmentRoutesAreNotPermittedAsPublicContent() throws Exception {
        assertNotPublicContent(getStatus("/foo/articles/slug"));
        assertNotPublicContent(getStatus("/tr/unknown/slug"));
        assertNotPublicContent(getStatus("/admin/content/anything"));
        assertNotPublicContent(getStatus("/actuator/foo/bar"));
        assertNotPublicContent(getStatus("/oauth2/authorization/foo"));
    }

    @Test
    void publicContentMatcherIsExplicitAndDoesNotUseBroadThreeSegmentPermit() throws Exception {
        assertThat(List.of(SecurityConfiguration.PUBLIC_CONTENT_GET_PATTERNS))
                .containsExactly(
                        "/tr/articles/*",
                        "/en/articles/*",
                        "/tr/notes/*",
                        "/en/notes/*",
                        "/tr/research/*",
                        "/en/research/*",
                        "/tr/pages/*",
                        "/en/pages/*");

        String securityConfiguration = Files.readString(Path.of("src/main/java/dev/persefonia/app/security/SecurityConfiguration.java"));
        String broadThreeSegmentMatcher = "/" + "*" + "/" + "*" + "/" + "*";
        assertThat(securityConfiguration).doesNotContain(quoted(broadThreeSegmentMatcher, '"'));
        assertThat(securityConfiguration).doesNotContain(quoted(broadThreeSegmentMatcher, '\''));
    }

    @Test
    void actuatorRoutesDoNotReceivePublicContentCacheHeaders() throws Exception {
        for (String path : List.of("/actuator", "/actuator/health", "/actuator/info", "/actuator/prometheus")) {
            mockMvc.perform(get(path))
                    .andExpect(status().is4xxClientError())
                    .andExpect(header().string("Cache-Control", not(Matchers.containsString("public"))));
        }
    }

    private int getStatus(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path))
                .andExpect(header().string("Cache-Control", not(Matchers.containsString("public"))))
                .andReturn();
        return result.getResponse().getStatus();
    }

    private static void assertNotPublicContent(int status) {
        assertThat(status).isBetween(300, 599);
        assertThat(status).isNotEqualTo(200);
    }

    private static String quoted(String value, char quote) {
        return quote + value + quote;
    }

    private void addProjected(ContentItem item, String publicPath) {
        items.add(item);
        routes.addFound(publicPath, item.id().value());
    }
}
