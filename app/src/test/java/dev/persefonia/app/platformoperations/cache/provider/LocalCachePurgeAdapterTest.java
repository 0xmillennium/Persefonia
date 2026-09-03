package dev.persefonia.app.platformoperations.cache.provider;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.platformoperations.application.cache.CachePurgeProviderRequest;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderTarget;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationTargetId;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import dev.persefonia.platformoperations.domain.cache.CachePurgeResult;
import dev.persefonia.platformoperations.domain.cache.CacheTargetStatus;
import dev.persefonia.platformoperations.domain.cache.CacheTargetType;
import dev.persefonia.platformoperations.domain.cache.CacheTargetValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalCachePurgeAdapterTest {
    @Test
    void treatsEveryEdgeTargetAsSkippedWithoutNetworkIo() {
        CachePurgeProviderRequest request = new CachePurgeProviderRequest(
                CacheInvalidationBatchId.newId(), 1, List.of(
                        target(CacheTargetType.URL, "/example"),
                        target(CacheTargetType.CACHE_TAG, "site:public-documents")));

        LocalCachePurgeAdapter adapter = new LocalCachePurgeAdapter();
        var result = adapter.purge(request);

        assertThat(adapter.provider()).isEqualTo(CachePurgeProvider.LOCAL);
        assertThat(result.result()).isEqualTo(CachePurgeResult.SUCCESS);
        assertThat(result.failureReason()).isNull();
        assertThat(result.outcomes()).allMatch(outcome -> outcome.status() == CacheTargetStatus.SKIPPED);
    }

    private static CachePurgeProviderTarget target(CacheTargetType type, String value) {
        return new CachePurgeProviderTarget(CacheInvalidationTargetId.newId(), type, CacheTargetValue.of(type, value));
    }
}
