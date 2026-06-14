package dev.persefonia.contentpublishing.application.query;

import java.util.Objects;
import java.util.UUID;

public record PublicContentBySourceQuery(
        UUID contentItemId,
        String expectedPublicPath) {
    public PublicContentBySourceQuery {
        Objects.requireNonNull(contentItemId, "contentItemId");
        Objects.requireNonNull(expectedPublicPath, "expectedPublicPath");
        if (expectedPublicPath.isBlank()) {
            throw new IllegalArgumentException("expectedPublicPath must not be blank");
        }
        if (!expectedPublicPath.startsWith("/") || expectedPublicPath.startsWith("//")) {
            throw new IllegalArgumentException("expectedPublicPath must be a path starting with /");
        }
        if (expectedPublicPath.contains("?")) {
            throw new IllegalArgumentException("expectedPublicPath must not contain a query string");
        }
        if (expectedPublicPath.contains("#")) {
            throw new IllegalArgumentException("expectedPublicPath must not contain a fragment");
        }
        if (expectedPublicPath.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("expectedPublicPath must not contain whitespace");
        }
    }
}
