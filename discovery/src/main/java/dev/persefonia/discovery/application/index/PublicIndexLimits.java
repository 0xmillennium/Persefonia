package dev.persefonia.discovery.application.index;

public final class PublicIndexLimits {
    public static final int MIN_SEARCH_QUERY_LENGTH = 2;
    public static final int MAX_SEARCH_QUERY_LENGTH = 120;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_SEARCH_LIMIT = 20;
    public static final int MAX_SEARCH_OFFSET = 10_000;
    public static final int MAX_SITEMAP_LIMIT = 50_000;
    public static final int MAX_FEED_LIMIT = 50;

    private PublicIndexLimits() {
    }

    public static int requireSearchLimit(int limit) {
        return requireRange(limit, MIN_LIMIT, MAX_SEARCH_LIMIT, "limit");
    }

    public static int requireSearchOffset(int offset) {
        return requireRange(offset, 0, MAX_SEARCH_OFFSET, "offset");
    }

    public static int requireSitemapLimit(int limit) {
        return requireRange(limit, MIN_LIMIT, MAX_SITEMAP_LIMIT, "limit");
    }

    public static int requireFeedLimit(int limit) {
        return requireRange(limit, MIN_LIMIT, MAX_FEED_LIMIT, "limit");
    }

    private static int requireRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
        return value;
    }
}
