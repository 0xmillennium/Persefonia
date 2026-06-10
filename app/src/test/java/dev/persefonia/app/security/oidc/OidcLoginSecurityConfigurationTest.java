package dev.persefonia.app.security.oidc;

import static org.mockito.Mockito.mock;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("oidc-login-test")
@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(OidcLoginSecurityConfigurationTest.OidcTestConfiguration.class)
class OidcLoginSecurityConfigurationTest {
    private final MockMvc mockMvc;

    @Autowired
    OidcLoginSecurityConfigurationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void unauthenticatedAdminRedirectsToAutheliaAuthorizationWhenClientRegistrationExists() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/oauth2/authorization/authelia"));
    }

    @Test
    void oauth2AuthorizationEndpointIsAvailableForAuthelia() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/authelia"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().dateValue("Expires", 0))
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://auth.example/authorize")));
    }

    @Test
    void formLoginStillDisabled() throws Exception {
        mockMvc.perform(post("/login").with(csrf()).param("username", "admin").param("password", "password"))
                .andExpect(status().isNotFound());
    }

    @Test
    void httpBasicStillDisabled() throws Exception {
        mockMvc.perform(get("/admin").header("Authorization", "Basic YWRtaW46cGFzc3dvcmQ="))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void publicHomeStillPublic() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void assetsStillPublic() throws Exception {
        mockMvc.perform(get("/assets/missing.css")).andExpect(status().isNotFound());
    }

    @Test
    void adminStillNotPublicWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("oidc-login-test")
    static class OidcTestConfiguration {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("authelia")
                    .clientId("test-client")
                    .clientSecret("fake-test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "email", "profile")
                    .authorizationUri("https://auth.example/authorize")
                    .tokenUri("https://auth.example/token")
                    .jwkSetUri("https://auth.example/jwks")
                    .userInfoUri("https://auth.example/userinfo")
                    .userNameAttributeName("sub")
                    .clientName("Authelia")
                    .build();
            return new InMemoryClientRegistrationRepository(registration);
        }

        @Bean
        PersefoniaOidcUserService persefoniaOidcUserService() {
            return mock(PersefoniaOidcUserService.class);
        }
    }
}
