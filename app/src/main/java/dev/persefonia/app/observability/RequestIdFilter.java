package dev.persefonia.app.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public final class RequestIdFilter extends OncePerRequestFilter {
    static final String MDC_KEY = "request_id";

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]+");

    private final RequestIdProperties properties;

    RequestIdFilter(RequestIdProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = effectiveRequestId(request);
        long startedAt = System.nanoTime();

        MDC.put(MDC_KEY, requestId);
        response.setHeader(properties.getResponseHeader(), requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            RequestRouteCategory routeCategory =
                    RequestRouteCategory.classify(request.getRequestURI(), response.getStatus());
            LOGGER.atInfo()
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("route_category", routeCategory.name())
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("duration_ms", durationMs)
                    .log("http_request_completed");
            MDC.remove(MDC_KEY);
        }
    }

    private String effectiveRequestId(HttpServletRequest request) {
        if (properties.isTrustIncomingHeader()) {
            String incomingRequestId = request.getHeader(properties.getIncomingHeader());
            if (isValid(incomingRequestId)) {
                return incomingRequestId;
            }
        }
        return UUID.randomUUID().toString();
    }

    private boolean isValid(String requestId) {
        return requestId != null
                && !requestId.isBlank()
                && requestId.length() <= properties.getMaxLength()
                && SAFE_REQUEST_ID.matcher(requestId).matches();
    }
}
