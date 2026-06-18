package dev.persefonia.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
class AdminMediaSecurityAndCacheTest {
    @Autowired MockMvc mockMvc;

    @Test
    void anonymousMediaAdminGetsAreProtectedAndUncached() throws Exception {
        assertProtectedAndUncached(mockMvc.perform(get("/admin/media")).andReturn());
        assertProtectedAndUncached(mockMvc.perform(get("/admin/media/new")).andReturn());
        assertProtectedAndUncached(mockMvc.perform(get(
                "/admin/media/11111111-1111-1111-1111-111111111111")).andReturn());
    }

    @Test
    void mediaAdminPostsRequireCsrfBeforeCommandHandling() throws Exception {
        assertThat(mockMvc.perform(multipart("/admin/media")).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(post("/admin/media/11111111-1111-1111-1111-111111111111")).andReturn()
                .getResponse().getStatus())
                .isEqualTo(403);

        assertProtectedAndUncached(mockMvc.perform(multipart("/admin/media").with(csrf())).andReturn());
        assertProtectedAndUncached(mockMvc.perform(post(
                "/admin/media/11111111-1111-1111-1111-111111111111").with(csrf())).andReturn());
    }

    private static void assertProtectedAndUncached(MvcResult result) {
        assertThat(result.getResponse().getStatus()).isBetween(300, 499);
        String cacheControl = result.getResponse().getHeader("Cache-Control");
        assertThat(cacheControl).contains("no-store").contains("private");
        assertThat(cacheControl).doesNotContain("public");
    }
}
