package dev.persefonia.webpublic.content;

import java.net.URI;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class PublicCanonicalUrlFactory {
    private final String publicBaseUrl;

    public PublicCanonicalUrlFactory(@Value("${site.public-base-url}") String publicBaseUrl) {
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
    }

    public String canonicalUrl(String canonicalPath) {
        Objects.requireNonNull(canonicalPath, "canonicalPath");
        if (!canonicalPath.startsWith("/")) {
            throw new IllegalArgumentException("canonicalPath must start with /");
        }
        return publicBaseUrl + canonicalPath;
    }

    private static String normalizeBaseUrl(String publicBaseUrl) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalArgumentException("publicBaseUrl must not be blank");
        }
        URI uri = URI.create(publicBaseUrl.strip());
        if (!uri.isAbsolute() || uri.getHost() == null) {
            throw new IllegalArgumentException("publicBaseUrl must be an absolute URL");
        }
        String normalized = uri.toString();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
