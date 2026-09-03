package dev.persefonia.app.platformoperations.cache.provider;

import dev.persefonia.platformoperations.domain.cache.CachePurgeFailureReason;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.http.HttpStatusCode;

final class CloudflareFailureClassifier {
    private CloudflareFailureClassifier() { }

    static CachePurgeFailureReason http(HttpStatusCode status) {
        return switch (status.value()) {
            case 400, 422 -> CachePurgeFailureReason.INVALID_TARGET;
            case 401, 403 -> CachePurgeFailureReason.AUTHENTICATION_ERROR;
            case 404 -> CachePurgeFailureReason.INVALID_CONFIGURATION;
            case 429 -> CachePurgeFailureReason.RATE_LIMITED;
            default -> status.is5xxServerError()
                    ? CachePurgeFailureReason.PROVIDER_5XX
                    : CachePurgeFailureReason.UNKNOWN_PROVIDER_FAILURE;
        };
    }

    static CachePurgeFailureReason transport(Throwable failure) {
        for (Throwable current = failure; current != null && current.getCause() != current;
                current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return CachePurgeFailureReason.TIMEOUT;
            }
        }
        return CachePurgeFailureReason.NETWORK_ERROR;
    }
}
