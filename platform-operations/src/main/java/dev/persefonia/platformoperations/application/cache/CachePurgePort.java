package dev.persefonia.platformoperations.application.cache;

import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;

public interface CachePurgePort {
    CachePurgeProvider provider();
    CachePurgeProviderResult purge(CachePurgeProviderRequest request);
}
