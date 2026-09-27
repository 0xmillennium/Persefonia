package dev.persefonia.app.security.oidc;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public final class AdminOidcSessionRevalidationFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminOidcSessionRevalidationFilter.class);
    private static final RequestMatcher ADMIN_REQUESTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/admin"),
            PathPatternRequestMatcher.withDefaults().matcher("/admin/**"));
    private final AdminOidcSessionProperties properties;
    private final AdminOidcSessionRevalidationService service;
    private final Clock clock;
    private final SecurityContextRepository contexts;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AdminOidcSessionRevalidationFilter(AdminOidcSessionProperties properties,
            AdminOidcSessionRevalidationService service, Clock clock, SecurityContextRepository contexts) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.service = Objects.requireNonNull(service, "service");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.ERROR || !ADMIN_REQUESTS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2) || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PersefoniaOidcUser currentUser)) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            chain.doFilter(request, response);
            return;
        }
        Instant now = clock.instant();
        boolean due = AdminOidcSessionState.lastSuccessfulRevalidation(request)
                .map(last -> !now.isBefore(last.plus(properties.getRevalidationInterval())))
                .orElse(true);
        if (!due) {
            chain.doFilter(request, response);
            return;
        }
        try {
            PersefoniaOidcUser freshUser = service.revalidate(oauth2, currentUser, request, response);
            OAuth2AuthenticationToken refreshed = new OAuth2AuthenticationToken(freshUser, freshUser.getAuthorities(),
                    oauth2.getAuthorizedClientRegistrationId());
            refreshed.setDetails(oauth2.getDetails());
            SecurityContext context = contextHolder.createEmptyContext();
            context.setAuthentication(refreshed);
            contextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            AdminOidcSessionState.markSuccessfulRevalidation(request, clock.instant());
        } catch (AdminOidcSessionRevalidationException exception) {
            AdminOidcSessionFailureReason reason = exception.reason();
            if (reason == AdminOidcSessionFailureReason.IDENTITY_PROVIDER_UNAVAILABLE) {
                LOGGER.warn("event=admin_session_revalidation_failed reason={}", reason);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            } else {
                LOGGER.info("event=admin_session_revalidation_failed reason={}", reason);
                // Clear request-attribute state too, so an ERROR dispatch cannot reload revoked authentication.
                contexts.saveContext(contextHolder.createEmptyContext(), request, response);
                session.invalidate();
                contextHolder.clearContext();
                response.sendError(reason == AdminOidcSessionFailureReason.AUTHENTICATION_EXPIRED
                        ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN);
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
