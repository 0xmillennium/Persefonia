package dev.persefonia.discovery.application.index;

import java.util.Objects;
import java.util.regex.Pattern;

public record PublicSearchRequest(
        String query,
        int limit,
        int offset) {
    private static final Pattern INTERNAL_WHITESPACE = Pattern.compile("\\s+");

    public PublicSearchRequest {
        query = normalize(query);
        if (query.length() < PublicIndexLimits.MIN_SEARCH_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                    "query must be at least " + PublicIndexLimits.MIN_SEARCH_QUERY_LENGTH + " characters");
        }
        if (query.length() > PublicIndexLimits.MAX_SEARCH_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                    "query must be at most " + PublicIndexLimits.MAX_SEARCH_QUERY_LENGTH + " characters");
        }
        limit = PublicIndexLimits.requireSearchLimit(limit);
        offset = PublicIndexLimits.requireSearchOffset(offset);
    }

    private static String normalize(String value) {
        Objects.requireNonNull(value, "query");
        if (value.chars().anyMatch(PublicSearchRequest::isForbiddenControlCharacter)) {
            throw new IllegalArgumentException("query must not contain control characters");
        }
        String normalized = INTERNAL_WHITESPACE.matcher(value.trim()).replaceAll(" ");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        return normalized;
    }

    private static boolean isForbiddenControlCharacter(int codePoint) {
        return Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint);
    }
}
