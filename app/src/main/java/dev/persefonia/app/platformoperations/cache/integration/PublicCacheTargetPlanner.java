package dev.persefonia.app.platformoperations.cache.integration;

import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequest;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationTargetRequest;
import dev.persefonia.platformoperations.domain.cache.CacheTargetType;
import dev.persefonia.platformoperations.domain.cache.InvalidationReason;
import dev.persefonia.platformoperations.domain.cache.InvalidationRequester;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

public final class PublicCacheTargetPlanner {
    public static final int MAX_EXPANDED_PUBLIC_TARGETS_PER_MUTATION = 500;
    public static final int DEPENDENCY_QUERY_LIMIT = MAX_EXPANDED_PUBLIC_TARGETS_PER_MUTATION + 1;

    public Optional<CacheInvalidationRequest> plan(Collection<String> routes) {
        Objects.requireNonNull(routes, "routes");
        TreeSet<String> canonical = new TreeSet<>();
        for (String route : routes) {
            if (route == null || route.isBlank() || !route.startsWith("/") || route.startsWith("//")
                    || route.contains("?") || route.contains("#") || route.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("public cache route must be a canonical path");
            }
            canonical.add(route);
            if (canonical.size() > MAX_EXPANDED_PUBLIC_TARGETS_PER_MUTATION) {
                throw new PublicCacheTargetOverflowException();
            }
        }
        if (canonical.isEmpty()) {
            return Optional.empty();
        }
        var targets = canonical.stream()
                .map(route -> new CacheInvalidationTargetRequest(CacheTargetType.URL, route))
                .toList();
        return Optional.of(new CacheInvalidationRequest(
                InvalidationReason.PUBLIC_RESOURCE_CHANGED, InvalidationRequester.SYSTEM, targets));
    }

    public static final class PublicCacheTargetOverflowException extends RuntimeException {
        public PublicCacheTargetOverflowException() {
            super("public cache invalidation target limit exceeded");
        }
    }
}
