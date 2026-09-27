package dev.persefonia.app.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

class AdminOidcAuthenticationSuccessHandlerTest {
    @Test
    void seedsOnlyInstantAndPreservesSavedRequestTarget() throws Exception {
        Instant now = Instant.parse("2026-09-26T12:00:00Z");
        var request = new MockHttpServletRequest("GET", "/admin/operations");
        var response = new MockHttpServletResponse();
        new HttpSessionRequestCache().saveRequest(request, response);
        new AdminOidcAuthenticationSuccessHandler(Clock.fixed(now, ZoneOffset.UTC))
                .onAuthenticationSuccess(request, response, new TestingAuthenticationToken("test", "test", "ROLE_OWNER"));
        assertThat(AdminOidcSessionState.lastSuccessfulRevalidation(request)).contains(now);
        assertThat(response.getRedirectedUrl()).contains("/admin/operations");
        assertThat(Collections.list(request.getSession().getAttributeNames()))
                .filteredOn(name -> name.startsWith("dev.persefonia.security.oidc."))
                .containsExactly("dev.persefonia.security.oidc.last-successful-revalidation");
    }

    @Test
    void timestampReadDoesNotCreateSession() {
        var request = new MockHttpServletRequest();
        assertThat(AdminOidcSessionState.lastSuccessfulRevalidation(request)).isEmpty();
        assertThat(request.getSession(false)).isNull();
    }
}
