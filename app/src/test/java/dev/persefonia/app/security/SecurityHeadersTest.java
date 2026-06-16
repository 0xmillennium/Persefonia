package dev.persefonia.app.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest(
        properties = {
                "management.server.port=0",
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "spring.flyway.enabled=false"
        })
@AutoConfigureMockMvc
@Import(TestPortfolioSettingsFallbackConfiguration.class)
class SecurityHeadersTest {
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                    + "object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'";
    private static final String PERMISSIONS_POLICY =
            "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicHomeHasContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", CONTENT_SECURITY_POLICY));
    }

    @Test
    void authenticatedAdminHasContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/admin")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", CONTENT_SECURITY_POLICY));
    }

    @Test
    void publicHomeHasPermissionsPolicy() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Permissions-Policy", PERMISSIONS_POLICY));
    }

    @Test
    void authenticatedAdminHasPermissionsPolicy() throws Exception {
        mockMvc.perform(get("/admin")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isOk())
                .andExpect(header().string("Permissions-Policy", PERMISSIONS_POLICY));
    }

    @Test
    void nosniffStillPresent() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void frameOptionsStillPresent() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", not(nullValue())));
    }

    @Test
    void referrerPolicyStillNoReferrer() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void hstsIsNotRequiredInLocalHttpProfile() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void unauthenticatedAdminRedirectHasSecurityHeaders() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is4xxClientError())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Permissions-Policy", PERMISSIONS_POLICY));
    }
}
