package dev.persefonia.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.identityaccess.domain.admin.AdminRole;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class AdminLogoutTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void postLogoutWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void postLogoutWithCsrfRedirectsToHome() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void postLogoutClearsSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/logout")
                        .session(session)
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void postLogoutResponseHasNoStoreHeaders() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().dateValue("Expires", 0))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void getLogoutIsNotSuccessful() throws Exception {
        mockMvc.perform(get("/logout")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().is4xxClientError());
    }
}
