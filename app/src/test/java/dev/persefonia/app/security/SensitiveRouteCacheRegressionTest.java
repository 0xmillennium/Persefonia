package dev.persefonia.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class SensitiveRouteCacheRegressionTest {
    @Autowired MockMvc mockMvc;

    @Test
    void unauthenticatedAdminAndPreviewRoutesRemainProtectedAndUncached() throws Exception {
        assertProtectedAndUncached(mockMvc.perform(get("/admin/content")).andReturn());
        assertProtectedAndUncached(mockMvc.perform(get("/admin/content/11111111-1111-1111-1111-111111111111/preview")).andReturn());
        assertProtectedAndUncached(mockMvc.perform(get("/admin/content/11111111-1111-1111-1111-111111111111/revisions")).andReturn());
    }

    @Test
    void oauthRoutesRemainUncached() throws Exception {
        assertUncached(mockMvc.perform(get("/oauth2/authorization/authelia")).andReturn());
        assertUncached(mockMvc.perform(get("/login/oauth2/code/authelia")).andReturn());
    }

    @Test
    void logoutIsNotPublicGetContentAndPostRemainsUncached() throws Exception {
        assertProtectedAndUncached(mockMvc.perform(get("/logout")).andReturn());
        assertUncached(mockMvc.perform(post("/logout")).andReturn());
    }

    private static void assertProtectedAndUncached(MvcResult result) {
        assertThat(result.getResponse().getStatus()).isBetween(300, 499);
        assertUncached(result);
    }

    private static void assertUncached(MvcResult result) {
        String cacheControl = result.getResponse().getHeader("Cache-Control");
        assertThat(cacheControl).contains("no-store").contains("private");
        assertThat(result.getResponse().getHeader("Pragma")).contains("no-cache");
        assertThat(result.getResponse().getHeaderValue("Expires")).isNotNull();
        assertThat(cacheControl).doesNotContain("public");
    }
}
