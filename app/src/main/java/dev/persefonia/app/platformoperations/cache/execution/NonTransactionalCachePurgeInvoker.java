package dev.persefonia.app.platformoperations.cache.execution;

import dev.persefonia.platformoperations.application.cache.CachePurgePort;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderRequest;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderResult;
import dev.persefonia.platformoperations.domain.cache.CachePurgeFailureReason;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import dev.persefonia.platformoperations.domain.cache.CacheTargetOutcome;
import dev.persefonia.platformoperations.domain.cache.CacheTargetStatus;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NonTransactionalCachePurgeInvoker {
    private final CachePurgePort purgePort;
    private final CachePurgeProvider provider;

    public NonTransactionalCachePurgeInvoker(CachePurgePort purgePort) {
        this.purgePort = Objects.requireNonNull(purgePort, "purgePort");
        this.provider = Objects.requireNonNull(purgePort.provider(), "purgePort.provider()");
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CachePurgeInvocationResult invoke(CachePurgeWorkItem workItem) {
        CachePurgeProviderRequest request = Objects.requireNonNull(workItem, "workItem").providerRequest();
        try {
            CachePurgeProviderResult result = Objects.requireNonNull(purgePort.purge(request), "provider result");
            result.validateFor(request);
            return new CachePurgeInvocationResult(provider, result);
        } catch (RuntimeException operationalFailure) {
            return new CachePurgeInvocationResult(provider, CachePurgeProviderResult.failed(request,
                    CachePurgeFailureReason.UNKNOWN_PROVIDER_FAILURE,
                    request.targets().stream()
                            .map(target -> CacheTargetOutcome.of(target.targetId(), CacheTargetStatus.FAILED))
                            .toList()));
        }
    }
}
