package dev.persefonia.app.security.oidc;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

final class AdminOidcSessionState {
    private static final String LAST_SUCCESSFUL_REVALIDATION =
            "dev.persefonia.security.oidc.last-successful-revalidation";

    private AdminOidcSessionState() {
    }

    static Optional<Instant> lastSuccessfulRevalidation(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.getAttribute(LAST_SUCCESSFUL_REVALIDATION);
        return value instanceof Instant instant ? Optional.of(instant) : Optional.empty();
    }

    static void markSuccessfulRevalidation(HttpServletRequest request, Instant instant) {
        request.getSession().setAttribute(LAST_SUCCESSFUL_REVALIDATION, Objects.requireNonNull(instant, "instant"));
    }
}
