package dev.persefonia.webadmin.discovery;

import java.util.Objects;

public record AdminRedirectRuleView(
        String id,
        String sourceUrl,
        String targetUrl,
        String statusCode,
        String reason,
        String activeLabel,
        boolean active,
        String sourceRef,
        String createdAt,
        String updatedAt,
        String version,
        String deactivateAction) {
    public AdminRedirectRuleView {
        requireNonBlank(id, "id");
        requireNonBlank(sourceUrl, "sourceUrl");
        requireNonBlank(targetUrl, "targetUrl");
        requireNonBlank(statusCode, "statusCode");
        requireNonBlank(reason, "reason");
        requireNonBlank(activeLabel, "activeLabel");
        requireNonBlank(sourceRef, "sourceRef");
        requireNonBlank(createdAt, "createdAt");
        requireNonBlank(updatedAt, "updatedAt");
        requireNonBlank(version, "version");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
