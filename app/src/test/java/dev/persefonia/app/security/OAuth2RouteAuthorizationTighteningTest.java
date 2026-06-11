package dev.persefonia.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import dev.persefonia.app.security.oidc.PersefoniaOidcUserService;

@ActiveProfiles("oauth2-route-tightening-test")
@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(OAuth2RouteAuthorizationTighteningTest.OidcTestConfiguration.class)
class OAuth2RouteAuthorizationTighteningTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void oauth2AuthorizationEndpointStillRedirectsWhenClientRegistrationExists() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/authelia"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://auth.example/authorize")));
    }

    @Test
    void oauth2CallbackEndpointIsNotBlockedByAuthorizationRules() throws Exception {
        int status = mockMvc.perform(get("/login/oauth2/code/authelia").param("error", "access_denied"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isNotEqualTo(403);
    }

    @Test
    void arbitraryOauth2PathIsNotPubliclySuccessful() throws Exception {
        mockMvc.perform(get("/oauth2/not-a-login-flow"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/oauth2/authorization/authelia"));
    }

    @Test
    void arbitraryLoginPathIsNotPubliclySuccessful() throws Exception {
        mockMvc.perform(get("/login/not-a-callback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/oauth2/authorization/authelia"));
    }

    @Test
    void postOauth2PathIsNotBroadlyPermitAll() throws Exception {
        mockMvc.perform(post("/oauth2/authorization/authelia"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    }

    @Test
    void publicHomeStillPublic() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void publicAssetsStillPublic() throws Exception {
        mockMvc.perform(get("/assets/missing.css")).andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("oauth2-route-tightening-test")
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
