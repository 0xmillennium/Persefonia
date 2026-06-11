package dev.persefonia.app.security.cache;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

public final class SensitiveRouteCacheHeadersFilter extends OncePerRequestFilter {
    static final String CACHE_CONTROL = "no-store, no-cache, max-age=0, must-revalidate, private";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isSensitivePath(pathWithinApplication(request))) {
            response.setHeader("Cache-Control", CACHE_CONTROL);
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
        }
        filterChain.doFilter(request, response);
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isEmpty()) {
            return requestUri;
        }
        if (requestUri.startsWith(contextPath)) {
            String path = requestUri.substring(contextPath.length());
            return path.isEmpty() ? "/" : path;
        }
        return requestUri;
    }

    private static boolean isSensitivePath(String path) {
        return path.equals("/admin")
                || path.startsWith("/admin/")
                || path.startsWith("/login/oauth2/code/")
                || path.startsWith("/oauth2/authorization/")
                || path.equals("/logout");
    }
}
