package dev.persefonia.platformoperations.application.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationTargetId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationValidationException;
import dev.persefonia.platformoperations.domain.cache.CachePurgeFailureReason;
import dev.persefonia.platformoperations.domain.cache.CachePurgeResult;
import dev.persefonia.platformoperations.domain.cache.CacheTargetOutcome;
import dev.persefonia.platformoperations.domain.cache.CacheTargetStatus;
import dev.persefonia.platformoperations.domain.cache.CacheTargetType;
import dev.persefonia.platformoperations.domain.cache.CacheTargetValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class CachePurgeProviderContractTest {
    private static final CachePurgeProviderTarget URL = new CachePurgeProviderTarget(
            CacheInvalidationTargetId.newId(), CacheTargetType.URL, CacheTargetValue.url("/example"));
    private static final CachePurgeProviderTarget TAG = new CachePurgeProviderTarget(
            CacheInvalidationTargetId.newId(), CacheTargetType.CACHE_TAG,
            CacheTargetValue.cacheTag("site:public-documents"));
    private static final CachePurgeProviderRequest REQUEST =
            new CachePurgeProviderRequest(CacheInvalidationBatchId.newId(), 1, List.of(URL, TAG));

    @Test
    void successfulResultRequiresCompleteNonFailedCoverage() {
        CachePurgeProviderResult result = CachePurgeProviderResult.success(REQUEST, List.of(
                CacheTargetOutcome.of(URL.targetId(), CacheTargetStatus.PURGED),
                CacheTargetOutcome.of(TAG.targetId(), CacheTargetStatus.SKIPPED)));

        assertThat(result.result()).isEqualTo(CachePurgeResult.SUCCESS);
        assertThat(result.failureReason()).isNull();
        assertThat(result.outcomes()).hasSize(2);

        assertThatThrownBy(() -> CachePurgeProviderResult.success(REQUEST, List.of(
                CacheTargetOutcome.of(URL.targetId(), CacheTargetStatus.PURGED),
                CacheTargetOutcome.of(TAG.targetId(), CacheTargetStatus.FAILED))))
                .isInstanceOf(CacheInvalidationValidationException.class);
    }

    @Test
    void failedResultRequiresReasonAndAtLeastOneFailedOutcome() {
        CachePurgeProviderResult result = CachePurgeProviderResult.failed(REQUEST,
                CachePurgeFailureReason.RATE_LIMITED, List.of(
                        CacheTargetOutcome.of(URL.targetId(), CacheTargetStatus.PURGED),
                        CacheTargetOutcome.of(TAG.targetId(), CacheTargetStatus.FAILED)));

        assertThat(result.result()).isEqualTo(CachePurgeResult.FAILED);
        assertThat(result.failureReason()).isEqualTo(CachePurgeFailureReason.RATE_LIMITED);

        assertThatThrownBy(() -> CachePurgeProviderResult.failed(REQUEST,
                CachePurgeFailureReason.RATE_LIMITED, List.of(
                        CacheTargetOutcome.of(URL.targetId(), CacheTargetStatus.PURGED),
                        CacheTargetOutcome.of(TAG.targetId(), CacheTargetStatus.SKIPPED))))
                .isInstanceOf(CacheInvalidationValidationException.class);
    }

    @Test
    void outcomesRejectMissingDuplicateAndUnknownTargetIdentities() {
        assertThatThrownBy(() -> CachePurgeProviderResult.success(REQUEST, List.of(
                CacheTargetOutcome.of(URL.targetId(), CacheTargetStatus.PURGED))))
                .isInstanceOf(CacheInvalidationValidationException.class);
        assertThatThrownBy(() -> CachePurgeProviderResult.success(REQUEST, List.of(
                CacheTargetOutcome.of(URL.targetId(), CacheTargetStatus.PURGED),
                CacheTargetOutcome.of(URL.targetId(), CacheTargetStatus.SKIPPED))))
                .isInstanceOf(CacheInvalidationValidationException.class);
        assertThatThrownBy(() -> CachePurgeProviderResult.success(REQUEST, List.of(
                CacheTargetOutcome.of(URL.targetId(), CacheTargetStatus.PURGED),
                CacheTargetOutcome.of(CacheInvalidationTargetId.newId(), CacheTargetStatus.SKIPPED))))
                .isInstanceOf(CacheInvalidationValidationException.class);
    }

    @Test
    void providerRequestRejectsEmptyDuplicateOrOutOfBudgetWork() {
        assertThatThrownBy(() -> new CachePurgeProviderRequest(
                CacheInvalidationBatchId.newId(), 1, List.of()))
                .isInstanceOf(CacheInvalidationValidationException.class);
        assertThatThrownBy(() -> new CachePurgeProviderRequest(
                CacheInvalidationBatchId.newId(), 4, List.of(URL)))
                .isInstanceOf(CacheInvalidationValidationException.class);
        assertThatThrownBy(() -> new CachePurgeProviderRequest(
                CacheInvalidationBatchId.newId(), 1, List.of(URL, URL)))
                .isInstanceOf(CacheInvalidationValidationException.class);
    }
}
