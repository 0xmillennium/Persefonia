package dev.persefonia.app.platformoperations.cache.provider;

import dev.persefonia.platformoperations.application.cache.CachePurgePort;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderRequest;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderResult;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import dev.persefonia.platformoperations.domain.cache.CacheTargetOutcome;
import dev.persefonia.platformoperations.domain.cache.CacheTargetStatus;

public final class LocalCachePurgeAdapter implements CachePurgePort {
    @Override
    public CachePurgeProvider provider() {
        return CachePurgeProvider.LOCAL;
    }

    @Override
    public CachePurgeProviderResult purge(CachePurgeProviderRequest request) {
        return CachePurgeProviderResult.success(request, request.targets().stream()
                .map(target -> CacheTargetOutcome.of(target.targetId(), CacheTargetStatus.SKIPPED))
                .toList());
    }
}
