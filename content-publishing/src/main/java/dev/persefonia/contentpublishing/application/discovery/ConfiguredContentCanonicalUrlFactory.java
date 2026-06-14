package dev.persefonia.contentpublishing.application.discovery;

import dev.persefonia.discovery.application.contract.CanonicalUrl;
import dev.persefonia.discovery.application.contract.PublicUrl;
import java.net.URI;
import java.util.Objects;

public final class ConfiguredContentCanonicalUrlFactory {
    private final String publicBaseUrl;

    public ConfiguredContentCanonicalUrlFactory(String publicBaseUrl) {
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
    }

    public CanonicalUrl canonicalUrl(PublicUrl publicUrl) {
        Objects.requireNonNull(publicUrl, "publicUrl");
        return new CanonicalUrl(publicBaseUrl + publicUrl.value());
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
