package dev.persefonia.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.persefonia.app.security.admin.AdminAuthorities;
import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.security.admin.AdminPrincipal;
import dev.persefonia.app.security.admin.PersefoniaAdminCommandActorResolver;
import dev.persefonia.app.security.oidc.PersefoniaOidcUser;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommand;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandActor;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(AdminCommandCsrfAndAuthorizationPatternTest.TestCommandConfiguration.class)
class AdminCommandCsrfAndAuthorizationPatternTest {
    private static final String TEST_COMMAND_PATH = "/admin/__test__/command";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestAdminCommandService testCommandService;

    @BeforeEach
    void resetExecutions() {
        testCommandService.reset();
    }

    @Test
    void ownerWithCsrfCanExecuteTestCommand() throws Exception {
        mockMvc.perform(post(TEST_COMMAND_PATH)
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(testCommandService.executions()).isEqualTo(1);
    }

    @Test
    void ownerWithoutCsrfIsForbiddenBeforeCommand() throws Exception {
        mockMvc.perform(post(TEST_COMMAND_PATH)
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isForbidden());

        assertThat(testCommandService.executions()).isZero();
    }

    @Test
    void editorWithCsrfIsForbiddenByApplicationPolicy() throws Exception {
        mockMvc.perform(post(TEST_COMMAND_PATH)
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR)))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(testCommandService.executions()).isZero();
    }

    @Test
    void disabledOwnerWithCsrfIsForbiddenByApplicationPolicy() throws Exception {
        mockMvc.perform(post(TEST_COMMAND_PATH)
                        .with(authentication(disabledOwnerAuthentication()))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(testCommandService.executions()).isZero();
    }

    @Test
    void genericAuthenticatedUserWithCsrfIsForbidden() throws Exception {
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken("generic-user", "password", AdminAuthorities.ROLE_ADMIN);
        authentication.setAuthenticated(true);

        mockMvc.perform(post(TEST_COMMAND_PATH)
                        .with(authentication(authentication))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(testCommandService.executions()).isZero();
    }

    @Test
    void anonymousWithCsrfIsForbidden() throws Exception {
        mockMvc.perform(post(TEST_COMMAND_PATH).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(testCommandService.executions()).isZero();
    }

    @Test
    void authorizationExceptionMapsTo403Not500() throws Exception {
        mockMvc.perform(post(TEST_COMMAND_PATH)
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCommandRouteDoesNotExistInProductionSource() throws IOException {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            assertThat(paths.filter(Files::isRegularFile)
                    .noneMatch(AdminCommandCsrfAndAuthorizationPatternTest::containsTestCommandPath))
                    .isTrue();
        }
    }

    private static Authentication disabledOwnerAuthentication() {
        AdminPrincipal principal = mock(AdminPrincipal.class);
        when(principal.accountId()).thenReturn(AdminAccountId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000001")));
        when(principal.status()).thenReturn(AdminAccountStatus.DISABLED);
        when(principal.roles()).thenReturn(Set.of(AdminRole.OWNER));

        Collection<GrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority(AdminAuthorities.ROLE_ADMIN),
                new SimpleGrantedAuthority(AdminAuthorities.ROLE_OWNER));

        PersefoniaOidcUser user = mock(PersefoniaOidcUser.class);
        when(user.adminPrincipal()).thenReturn(principal);
        when(user.getName()).thenReturn("00000000-0000-0000-0000-000000000001");
        doReturn(authorities).when(user).getAuthorities();

        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "authelia");
    }

    private static boolean containsTestCommandPath(Path path) {
        try {
            return Files.readString(path).contains(TEST_COMMAND_PATH);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read production source", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestCommandConfiguration {
        @Bean
        TestAdminCommandService testAdminCommandService(AdminCommandAuthorizationPolicy policy) {
            return new TestAdminCommandService(policy);
        }

    }

    static final class TestAdminCommandService {
        private final AdminCommandAuthorizationPolicy policy;
        private final AtomicInteger executions = new AtomicInteger();

        TestAdminCommandService(AdminCommandAuthorizationPolicy policy) {
            this.policy = policy;
        }

        void execute(AdminCommandActor actor) {
            policy.requireOwner(actor, AdminCommand.named("test.admin.mutate"));
            executions.incrementAndGet();
        }

        int executions() {
            return executions.get();
        }

        void reset() {
            executions.set(0);
        }
    }

    @RestController
    static final class TestAdminCommandEndpoint {
        private final PersefoniaAdminCommandActorResolver actorResolver;
        private final TestAdminCommandService testCommandService;

        TestAdminCommandEndpoint(
                PersefoniaAdminCommandActorResolver actorResolver,
                TestAdminCommandService testCommandService) {
            this.actorResolver = actorResolver;
            this.testCommandService = testCommandService;
        }

        @PostMapping(TEST_COMMAND_PATH)
        ResponseEntity<Void> run(Authentication authentication) {
            AdminCommandActor actor = actorResolver.resolve(authentication);
            testCommandService.execute(actor);
            return ResponseEntity.noContent().build();
        }
    }
}
