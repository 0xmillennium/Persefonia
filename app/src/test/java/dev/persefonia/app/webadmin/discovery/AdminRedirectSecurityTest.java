package dev.persefonia.app.webadmin.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.discovery.AdminRedirectTestConfiguration.AdminRedirectTestPorts;
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
@Import(AdminRedirectTestConfiguration.class)
@ActiveProfiles({"test", "admin-redirect-mvc-test"})
class AdminRedirectSecurityTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminRedirectTestPorts ports;

    @BeforeEach
    void reset() {
        ports.reset();
    }

    @Test
    void adminRedirectRoutesRequireAuthenticatedOwner() throws Exception {
        var editor = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));

        mockMvc.perform(get("/admin/discovery/redirects")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/discovery/redirects").with(editor)).andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/discovery/redirects")
                        .with(editor)
                        .with(csrf())
                        .param("sourceUrl", "/tr/articles/old")
                        .param("targetUrl", "/tr/articles/new")
                        .param("statusCode", "301"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/discovery/redirects/22222222-2222-2222-2222-222222222222/deactivate")
                        .with(editor)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRedirectPostsRequireCsrf() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(post("/admin/discovery/redirects").with(owner)).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/discovery/redirects/22222222-2222-2222-2222-222222222222/deactivate")
                        .with(owner))
                .andExpect(status().isForbidden());

        assertThat(ports.lastCreateCommand()).isNull();
        assertThat(ports.lastDeactivateCommand()).isNull();
    }
}
