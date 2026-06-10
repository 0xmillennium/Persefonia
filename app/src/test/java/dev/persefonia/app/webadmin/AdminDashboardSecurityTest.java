package dev.persefonia.app.webadmin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.identityaccess.domain.admin.AdminRole;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class AdminDashboardSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedAdminDoesNotExposeShell() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(not(containsString("Persefonia Admin"))))
                .andExpect(content().string(not(containsString("Logout"))))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void authenticatedPersefoniaAdminCanViewDashboard() throws Exception {
        mockMvc.perform(get("/admin")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Persefonia Admin")))
                .andExpect(content().string(containsString("Ada Admin")))
                .andExpect(content().string(containsString("Dashboard")))
                .andExpect(content().string(containsString("Logout")))
                .andExpect(content().string(containsString("Owner")))
                .andExpect(content().string(not(containsString("fake-id-token-value"))))
                .andExpect(content().string(not(containsString("accessToken"))))
                .andExpect(content().string(not(containsString("refreshToken"))));
    }

    @Test
    void genericAuthenticatedUserCannotViewDashboard() throws Exception {
        mockMvc.perform(get("/admin").with(authentication(new TestingAuthenticationToken("user", "password"))))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("Persefonia Admin"))));
    }

    @Test
    void dashboardResponseHasXRequestId() throws Exception {
        mockMvc.perform(get("/admin")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR))))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void dashboardResponseHasNoStoreHeaders() throws Exception {
        mockMvc.perform(get("/admin")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().dateValue("Expires", 0));
    }
}
