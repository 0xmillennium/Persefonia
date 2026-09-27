package dev.persefonia.app.security.oidc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.persefonia.app.security.admin.AdminPrincipal;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
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

import dev.persefonia.app.TestPortfolioSettingsFallbackConfiguration;

@ActiveProfiles("oidc-login-test")
@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import({
        OidcLoginSecurityConfigurationTest.OidcTestConfiguration.class,
        TestPortfolioSettingsFallbackConfiguration.class
})
class OidcLoginSecurityConfigurationTest {
    @MockitoBean
    private AdminOidcSessionRevalidationService revalidation;

    @Autowired
    private OAuth2AuthorizedClientRepository authorizedClients;

    @Autowired
    private SecurityFilterChain applicationSecurityFilterChain;

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
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/oauth2/authorization/authelia"));
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

    @Test
    void oidcStateIsSessionScopedAndRevalidationRunsBeforeAuthorization() {
        assertThat(authorizedClients).isInstanceOf(HttpSessionOAuth2AuthorizedClientRepository.class);
        var filters = applicationSecurityFilterChain.getFilters();
        int revalidationIndex = java.util.stream.IntStream.range(0, filters.size())
                .filter(index -> filters.get(index) instanceof AdminOidcSessionRevalidationFilter).findFirst().orElseThrow();
        int authorizationIndex = java.util.stream.IntStream.range(0, filters.size())
                .filter(index -> filters.get(index) instanceof AuthorizationFilter).findFirst().orElseThrow();
        assertThat(revalidationIndex).isLessThan(authorizationIndex);
    }

    @Test
    void roleUpgradeAuthorizesSameRequestAndPersistsForNextRequest() throws Exception {
        var editor = new PersefoniaOidcUser(OidcTestFixtures.validUser(),
                AdminPrincipal.from(AdminOidcSessionRevalidationServiceTest.account(AdminRole.EDITOR)));
        var owner = new PersefoniaOidcUser(OidcTestFixtures.validUser(),
                AdminPrincipal.from(AdminOidcSessionRevalidationServiceTest.account(AdminRole.OWNER)));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(editor, editor.getAuthorities(), "authelia"));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        when(revalidation.revalidate(any(), any(), any(), any())).thenReturn(owner);
        mockMvc.perform(get("/admin/operations/authority-test").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/admin/operations/authority-test").session(session)).andExpect(status().isOk());
        verify(revalidation, times(1)).revalidate(any(), any(), any(), any());
        var saved = (org.springframework.security.core.context.SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(saved.getAuthentication().getAuthorities()).extracting(authority -> authority.getAuthority())
                .contains("ROLE_OWNER").doesNotContain("ROLE_EDITOR");
    }

    @RestController
    static class AuthorityTestController {
        @GetMapping("/admin/operations/authority-test")
        String authority() {
            return "authorized";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("oidc-login-test")
    static class OidcTestConfiguration {
        @Bean
        AuthorityTestController authorityTestController() {
            return new AuthorityTestController();
        }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("authelia")
                    .clientId("test-client")
                    .clientSecret("fake-test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "email", "profile", "groups", "offline_access")
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
