package dev.persefonia.app.webadmin.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.projects.AdminProjectTestConfiguration.AdminProjectTestRepository;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
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
@Import(AdminProjectTestConfiguration.class)
@ActiveProfiles({"test", "admin-project-mvc-test"})
class AdminProjectSecurityAndCacheTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminProjectTestRepository projects;

    @BeforeEach
    void reset() {
        projects.reset();
    }

    @Test
    void getRequiresAdminAndHasSensitiveCacheHeaders() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(get("/admin/projects")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/projects").with(owner))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    @Test
    void postRequiresCsrfAndOwnerAuthorizationInApplicationLayer() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
        var editor = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));

        mockMvc.perform(validPost().with(owner)).andExpect(status().isForbidden());
        mockMvc.perform(validPost().with(editor).with(csrf())).andExpect(status().isForbidden());

        assertThat(projects.all()).isEmpty();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validPost() {
        return post("/admin/projects")
                .param("status", "EXPERIMENT")
                .param("visibility", "PRIVATE")
                .param("trEnabled", "true")
                .param("trSlug", "sample-project")
                .param("trTitle", "Sample Project")
                .param("trSummary", "Summary");
    }
}
