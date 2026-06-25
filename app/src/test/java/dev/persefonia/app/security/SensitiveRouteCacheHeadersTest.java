package dev.persefonia.app.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import dev.persefonia.app.TestPortfolioSettingsFallbackConfiguration;
import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.identityaccess.domain.admin.AdminRole;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(TestPortfolioSettingsFallbackConfiguration.class)
class SensitiveRouteCacheHeadersTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminRouteHasNoStoreWhenUnauthenticated() throws Exception {
        assertNoStore(mockMvc.perform(get("/admin")).andExpect(status().is4xxClientError()));
    }

    @Test
    void adminRouteHasNoStoreWhenAuthenticated() throws Exception {
        assertNoStore(mockMvc.perform(get("/admin")
                .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR))))
                .andExpect(status().isOk()));
    }

    @Test
    void oauth2AuthorizationRouteHasNoStore() throws Exception {
        assertNoStore(mockMvc.perform(get("/oauth2/authorization/authelia"))
                .andExpect(status().isNotFound()));
    }

    @Test
    void logoutPostHasNoStore() throws Exception {
        assertNoStore(mockMvc.perform(post("/logout")
                .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                .with(csrf()))
                .andExpect(status().isFound()));
    }

    @Test
    void contactRouteHasNoStore() throws Exception {
        assertNoStore(mockMvc.perform(get("/contact")).andExpect(status().isOk()));
    }

    @Test
    void loginOauth2CallbackRouteHasNoStoreIfPresent() throws Exception {
        assertNoStore(mockMvc.perform(get("/login/oauth2/code/authelia")).andExpect(status().isNotFound()));
    }

    @Test
    void publicHomeStillWorks() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    private static void assertNoStore(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        result.andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().dateValue("Expires", 0));
    }
}
