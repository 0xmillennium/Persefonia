package dev.persefonia.app.webadmin.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.discovery.AdminRedirectTestConfiguration.AdminRedirectTestPorts;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import java.util.UUID;
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
class AdminRedirectControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminRedirectTestPorts ports;

    @BeforeEach
    void reset() {
        ports.reset();
    }

    @Test
    void ownerCanViewRedirectsListWithNoStoreAndNoindex() throws Exception {
        ports.addRule(AdminRedirectTestPorts.activeRule(UUID.fromString("11111111-1111-1111-1111-111111111111")));

        mockMvc.perform(get("/admin/discovery/redirects")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().string(containsString("noindex,nofollow,noarchive")))
                .andExpect(content().string(containsString("Create manual redirect")))
                .andExpect(content().string(containsString("/tr/articles/old")))
                .andExpect(content().string(containsString("Deactivate")));
    }

    @Test
    void ownerCanCreateManualRedirectWithNoSourceReference() throws Exception {
        mockMvc.perform(post("/admin/discovery/redirects")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf())
                        .param("sourceUrl", "/tr/articles/old")
                        .param("targetUrl", "/tr/articles/new")
                        .param("statusCode", "307"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/discovery/redirects?created=true"));

        assertThat(ports.lastCreateCommand()).satisfies(command -> {
            assertThat(command.sourceUrl().value()).isEqualTo("/tr/articles/old");
            assertThat(command.targetUrl().value()).isEqualTo("/tr/articles/new");
            assertThat(command.statusCode()).isEqualTo(RedirectStatusCode.TEMPORARY_REDIRECT_307);
            assertThat(command.actor().active()).isTrue();
            assertThat(command.actor().owner()).isTrue();
        });
    }

    @Test
    void ownerCanDeactivateActiveRedirect() throws Exception {
        UUID redirectId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        mockMvc.perform(post("/admin/discovery/redirects/" + redirectId + "/deactivate")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/discovery/redirects?deactivated=true"));

        assertThat(ports.lastDeactivateCommand().redirectRuleId().value()).isEqualTo(redirectId);
        assertThat(ports.lastDeactivateCommand().actor().active()).isTrue();
        assertThat(ports.lastDeactivateCommand().actor().owner()).isTrue();
    }

    @Test
    void anonymousCannotViewCreateOrDeactivate() throws Exception {
        mockMvc.perform(get("/admin/discovery/redirects")).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/admin/discovery/redirects").with(csrf())).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/admin/discovery/redirects/22222222-2222-2222-2222-222222222222/deactivate")
                        .with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void nonOwnerCannotViewCreateOrDeactivate() throws Exception {
        var editor = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));
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

        assertThat(ports.lastCreateCommand()).isNull();
        assertThat(ports.lastDeactivateCommand()).isNull();
    }

    @Test
    void createAndDeactivateRequireCsrf() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
        mockMvc.perform(post("/admin/discovery/redirects").with(owner)).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/discovery/redirects/22222222-2222-2222-2222-222222222222/deactivate")
                        .with(owner))
                .andExpect(status().isForbidden());

        assertThat(ports.lastCreateCommand()).isNull();
        assertThat(ports.lastDeactivateCommand()).isNull();
    }

    @Test
    void invalidSourceTargetStatusAndSelfRedirectShowSafeErrors() throws Exception {
        expectFormError("sourceUrl", "https://example.test/old", "/tr/articles/new", "301", "sourceUrl:");
        expectFormError("targetUrl", "/tr/articles/old", "//external", "301", "targetUrl:");
        expectFormError("statusCode", "/tr/articles/old", "/tr/articles/new", "303", "statusCode:");
        expectFormError("targetUrl", "/tr/articles/same", "/tr/articles/same", "301", "Target path must differ");
    }

    @Test
    void duplicateAndDirectLoopResultsShowSafeErrors() throws Exception {
        ports.rejectCreate(RedirectRuleCreationResult.Reason.DUPLICATE_ACTIVE_SOURCE);
        postValidCreate()
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("An active redirect already exists")));

        ports.rejectCreate(RedirectRuleCreationResult.Reason.LOOP_DETECTED);
        postValidCreate()
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("direct loop")));
    }

    @Test
    void unexpectedGatewayFailuresPropagateWithoutFailedRedirectOrBusinessResult() {
        IllegalStateException createFailure = new IllegalStateException("private create failure");
        ports.failCreate(createFailure);

        assertThatThrownBy(this::postValidCreate)
                .hasCause(createFailure);

        IllegalStateException deactivateFailure = new IllegalStateException("private deactivate failure");
        ports.failDeactivate(deactivateFailure);
        assertThatThrownBy(() -> mockMvc.perform(post(
                                "/admin/discovery/redirects/22222222-2222-2222-2222-222222222222/deactivate")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf())))
                .hasCause(deactivateFailure);
    }

    @Test
    void obsoleteFailedQueryFlagDoesNotRenderMessageOrRawFailure() throws Exception {
        mockMvc.perform(get("/admin/discovery/redirects")
                        .param("failed", "true")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Redirect action failed."))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("private failure"))));
    }

    private void expectFormError(
            String field,
            String sourceUrl,
            String targetUrl,
            String statusCode,
            String expectedText) throws Exception {
        mockMvc.perform(post("/admin/discovery/redirects")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf())
                        .param("sourceUrl", sourceUrl)
                        .param("targetUrl", targetUrl)
                        .param("statusCode", statusCode))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(field)))
                .andExpect(content().string(containsString(expectedText)))
                .andExpect(content().string(containsString("Create manual redirect")));
    }

    private org.springframework.test.web.servlet.ResultActions postValidCreate() throws Exception {
        return mockMvc.perform(post("/admin/discovery/redirects")
                .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                .with(csrf())
                .param("sourceUrl", "/tr/articles/old")
                .param("targetUrl", "/tr/articles/new")
                .param("statusCode", "301"));
    }
}
