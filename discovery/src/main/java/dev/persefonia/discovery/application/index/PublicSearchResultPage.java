package dev.persefonia.discovery.application.index;

import java.util.List;
import java.util.Objects;

public record PublicSearchResultPage(
        String normalizedQuery,
        int limit,
        int offset,
        long totalCount,
        List<PublicSearchResult> results) {
    public PublicSearchResultPage {
        requireNonBlank(normalizedQuery, "normalizedQuery");
        limit = PublicIndexLimits.requireSearchLimit(limit);
        offset = PublicIndexLimits.requireSearchOffset(offset);
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative");
        }
        results = List.copyOf(Objects.requireNonNull(results, "results"));
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
