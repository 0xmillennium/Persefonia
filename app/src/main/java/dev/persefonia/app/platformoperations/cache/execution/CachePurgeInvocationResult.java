package dev.persefonia.app.platformoperations.cache.execution;

import dev.persefonia.platformoperations.application.cache.CachePurgeProviderResult;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import java.util.Objects;

record CachePurgeInvocationResult(CachePurgeProvider provider, CachePurgeProviderResult result) {
    CachePurgeInvocationResult {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(result, "result");
    }
}
