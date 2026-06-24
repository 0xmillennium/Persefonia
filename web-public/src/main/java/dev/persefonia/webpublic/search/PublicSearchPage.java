package dev.persefonia.webpublic.search;

import java.util.List;
import java.util.Objects;

public record PublicSearchPage(
        String query,
        String normalizedQuery,
        boolean hasQuery,
        boolean hasValidationError,
        String validationError,
        List<PublicSearchResultItem> results,
        long totalCount,
        int currentPage,
        int totalPages,
        boolean hasPreviousPage,
        boolean hasNextPage,
        String previousPageUrl,
        String nextPageUrl,
        String canonicalUrl,
        List<String> stylesheetPaths) {
    public PublicSearchPage {
        query = Objects.requireNonNull(query, "query");
        normalizedQuery = Objects.requireNonNull(normalizedQuery, "normalizedQuery");
        validationError = Objects.requireNonNull(validationError, "validationError");
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative");
        }
        if (currentPage < 1) {
            throw new IllegalArgumentException("currentPage must be positive");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages must not be negative");
        }
        previousPageUrl = Objects.requireNonNull(previousPageUrl, "previousPageUrl");
        nextPageUrl = Objects.requireNonNull(nextPageUrl, "nextPageUrl");
        canonicalUrl = requireNonBlank(canonicalUrl, "canonicalUrl");
        stylesheetPaths = List.copyOf(Objects.requireNonNull(stylesheetPaths, "stylesheetPaths"));
    }

    public boolean hasResults() {
        return !results.isEmpty();
    }

    public boolean hasSearched() {
        return hasQuery && !hasValidationError;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
