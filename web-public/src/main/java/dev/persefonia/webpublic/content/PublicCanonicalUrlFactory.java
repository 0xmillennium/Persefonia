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
        URI uri;
        try {
            uri = new URI(publicBaseUrl.strip());
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("publicBaseUrl must be a valid URL", exception);
        }
        if (!uri.isAbsolute() || uri.getHost() == null) {
            throw new IllegalArgumentException("publicBaseUrl must be an absolute URL");
        }
        String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("publicBaseUrl scheme must be http or https");
        }
        if (uri.getQuery() != null) {
            throw new IllegalArgumentException("publicBaseUrl must not contain a query");
        }
        if (uri.getFragment() != null) {
            throw new IllegalArgumentException("publicBaseUrl must not contain a fragment");
        }
        String normalized = uri.toString();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
