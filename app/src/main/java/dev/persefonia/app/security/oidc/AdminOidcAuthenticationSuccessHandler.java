package dev.persefonia.app.security.oidc;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

public final class AdminOidcAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private final Clock clock;
    private final AuthenticationSuccessHandler delegate = new SavedRequestAwareAuthenticationSuccessHandler();

    public AdminOidcAuthenticationSuccessHandler(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        AdminOidcSessionState.markSuccessfulRevalidation(request, clock.instant());
        delegate.onAuthenticationSuccess(request, response, authentication);
    }
}
