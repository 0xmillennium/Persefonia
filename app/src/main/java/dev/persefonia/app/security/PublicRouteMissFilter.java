package dev.persefonia.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** Terminates safe public misses without granting access to an unregistered controller. */
final class PublicRouteMissFilter extends OncePerRequestFilter {
    private final List<RequestMatcher> publicRoutes = Stream.of(
            SecurityConfiguration.PUBLIC_RESOURCE_GET_PATTERNS,
            SecurityConfiguration.PUBLIC_CONTENT_GET_PATTERNS,
            SecurityConfiguration.PUBLIC_TAG_GET_PATTERNS,
            SecurityConfiguration.PUBLIC_SERIES_GET_PATTERNS,
            SecurityConfiguration.PUBLIC_PROJECT_GET_PATTERNS)
            .flatMap(Stream::of)
            .map(pattern -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(pattern))
            .toList();
    private final List<RequestMatcher> privateRoutes = Stream.of(
            "/admin", "/oauth2", "/login", "/logout", "/actuator", "/error", "/api", "/internal", "/media")
            .flatMap(namespace -> Stream.of(namespace, namespace + "/**"))
            .map(pattern -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(pattern))
            .toList();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getDispatcherType() == jakarta.servlet.DispatcherType.REQUEST
                && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                && privateRoutes.stream().noneMatch(route -> route.matches(request))
                && publicRoutes.stream().noneMatch(route -> route.matches(request))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
