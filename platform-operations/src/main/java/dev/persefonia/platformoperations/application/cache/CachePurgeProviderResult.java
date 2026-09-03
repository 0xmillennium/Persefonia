package dev.persefonia.platformoperations.application.cache;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationTargetId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationValidationException;
import dev.persefonia.platformoperations.domain.cache.CachePurgeFailureReason;
import dev.persefonia.platformoperations.domain.cache.CachePurgeResult;
import dev.persefonia.platformoperations.domain.cache.CacheTargetOutcome;
import dev.persefonia.platformoperations.domain.cache.CacheTargetStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CachePurgeProviderResult {
    private final CachePurgeResult result;
    private final CachePurgeFailureReason failureReason;
    private final List<CacheTargetOutcome> outcomes;

    private CachePurgeProviderResult(
            CachePurgeProviderRequest request,
            CachePurgeResult result,
            CachePurgeFailureReason failureReason,
            List<CacheTargetOutcome> outcomes) {
        Objects.requireNonNull(request, "request");
        this.result = Objects.requireNonNull(result, "result");
        this.failureReason = failureReason;
        this.outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        validateSemantics();
        validateCoverage(request);
    }

    public static CachePurgeProviderResult success(
            CachePurgeProviderRequest request, List<CacheTargetOutcome> outcomes) {
        return new CachePurgeProviderResult(request, CachePurgeResult.SUCCESS, null, outcomes);
    }

    public static CachePurgeProviderResult failed(
            CachePurgeProviderRequest request,
            CachePurgeFailureReason failureReason,
            List<CacheTargetOutcome> outcomes) {
        return new CachePurgeProviderResult(request, CachePurgeResult.FAILED,
                Objects.requireNonNull(failureReason, "failureReason"), outcomes);
    }

    public void validateFor(CachePurgeProviderRequest request) {
        Objects.requireNonNull(request, "request");
        validateSemantics();
        validateCoverage(request);
    }

    private void validateSemantics() {
        boolean failedOutcome = outcomes.stream().anyMatch(outcome -> outcome.status() == CacheTargetStatus.FAILED);
        if (result == CachePurgeResult.SUCCESS && (failureReason != null || failedOutcome)) {
            throw invalid("successful provider result cannot contain failure state");
        }
        if (result == CachePurgeResult.FAILED && (failureReason == null || !failedOutcome)) {
            throw invalid("failed provider result requires a safe failure reason and a failed outcome");
        }
    }

    private void validateCoverage(CachePurgeProviderRequest request) {
        Set<CacheInvalidationTargetId> requested = new HashSet<>();
        request.targets().forEach(target -> requested.add(target.targetId()));
        Set<CacheInvalidationTargetId> returned = new HashSet<>();
        for (CacheTargetOutcome outcome : outcomes) {
            if (outcome == null || !requested.contains(outcome.targetId()) || !returned.add(outcome.targetId())) {
                throw invalid("provider outcomes must match every requested target exactly once");
            }
        }
        if (!returned.equals(requested)) {
            throw invalid("provider outcomes must match every requested target exactly once");
        }
    }

    private static CacheInvalidationValidationException invalid(String message) {
        return new CacheInvalidationValidationException(message);
    }

    public CachePurgeResult result() { return result; }
    public CachePurgeFailureReason failureReason() { return failureReason; }
    public List<CacheTargetOutcome> outcomes() { return outcomes; }
}
