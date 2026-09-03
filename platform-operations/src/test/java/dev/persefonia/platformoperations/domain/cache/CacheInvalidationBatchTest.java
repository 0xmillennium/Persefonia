package dev.persefonia.platformoperations.domain.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CacheInvalidationBatchTest {
    private static final Instant REQUESTED_AT = Instant.parse("2026-09-03T10:00:00Z");
    private static final Instant ATTEMPTED_AT = Instant.parse("2026-09-03T10:01:00Z");
    private static final Instant RECORDED_AT = Instant.parse("2026-09-03T10:02:00Z");

    @Test
    void requestCreatesPristineStateAndDeduplicatesByTypeAndValue() {
        CacheInvalidationTarget first = url("/articles/example");
        CacheInvalidationBatch batch = CacheInvalidationBatch.request(CacheInvalidationBatchId.newId(),
                InvalidationReason.PUBLIC_RESOURCE_CHANGED, InvalidationRequester.SYSTEM, REQUESTED_AT,
                List.of(first, url("/articles/example"), tag("site:public-documents")));

        assertThat(batch.status()).isEqualTo(CacheInvalidationStatus.REQUESTED);
        assertThat(batch.targets()).hasSize(2).allMatch(target -> target.status() == CacheTargetStatus.PENDING);
        assertThat(batch.targets().getFirst().id()).isEqualTo(first.id());
        assertThat(batch.attempts()).isEmpty();
        assertThat(batch.completedAt()).isEmpty();
        assertThat(batch.failureReason()).isEmpty();
        assertThat(batch.version()).isZero();
        assertThatThrownBy(() -> batch.targets().add(url("/other")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> CacheInvalidationBatch.request(CacheInvalidationBatchId.newId(),
                InvalidationReason.PUBLIC_RESOURCE_CHANGED, InvalidationRequester.SYSTEM, REQUESTED_AT, List.of()))
                .isInstanceOf(CacheInvalidationValidationException.class);
    }

    @Test
    void initialAttemptOnlyReservesExecution() {
        CacheInvalidationBatch batch = requestedBatch();
        batch.beginInitialAttempt();

        assertThat(batch.status()).isEqualTo(CacheInvalidationStatus.RUNNING);
        assertThat(batch.version()).isEqualTo(1);
        assertThat(batch.attempts()).isEmpty();
        assertThat(batch.targets()).allMatch(target -> target.status() == CacheTargetStatus.PENDING);
        assertThatThrownBy(batch::beginInitialAttempt).isInstanceOf(CacheInvalidationValidationException.class);
    }

    @Test
    void successfulAttemptCompletesAndRetainsTerminalTargetOutcomes() {
        CacheInvalidationBatch batch = requestedBatch();
        batch.beginInitialAttempt();
        batch.recordAttemptResult(1, CachePurgeProvider.LOCAL, ATTEMPTED_AT, CachePurgeResult.SUCCESS, null,
                List.of(outcome(batch, 0, CacheTargetStatus.PURGED), outcome(batch, 1, CacheTargetStatus.SKIPPED)),
                RECORDED_AT);

        assertThat(batch.status()).isEqualTo(CacheInvalidationStatus.COMPLETED);
        assertThat(batch.attempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.attemptNumber()).isEqualTo(1);
            assertThat(attempt.result()).isEqualTo(CachePurgeResult.SUCCESS);
        });
        assertThat(batch.completedAt()).contains(RECORDED_AT);
        assertThat(batch.failureReason()).isEmpty();
        assertThat(batch.version()).isEqualTo(2);
        assertThatThrownBy(batch::beginManualRetry).isInstanceOf(CacheInvalidationValidationException.class);
    }

    @Test
    void failureAndPartialFailureDeriveRetryableState() {
        CacheInvalidationBatch failed = requestedBatch();
        failed.beginInitialAttempt();
        failed.recordAttemptResult(1, CachePurgeProvider.CLOUDFLARE, ATTEMPTED_AT, CachePurgeResult.FAILED,
                CachePurgeFailureReason.TIMEOUT,
                List.of(outcome(failed, 0, CacheTargetStatus.FAILED), outcome(failed, 1, CacheTargetStatus.FAILED)),
                RECORDED_AT);
        assertThat(failed.status()).isEqualTo(CacheInvalidationStatus.FAILED);
        assertThat(failed.completedAt()).isEmpty();

        CacheInvalidationBatch partial = requestedBatch();
        partial.beginInitialAttempt();
        partial.recordAttemptResult(1, CachePurgeProvider.CLOUDFLARE, ATTEMPTED_AT, CachePurgeResult.FAILED,
                CachePurgeFailureReason.NETWORK_ERROR,
                List.of(outcome(partial, 0, CacheTargetStatus.PURGED), outcome(partial, 1, CacheTargetStatus.FAILED)),
                RECORDED_AT);
        assertThat(partial.status()).isEqualTo(CacheInvalidationStatus.PARTIAL);
        partial.beginManualRetry();
        assertThat(partial.status()).isEqualTo(CacheInvalidationStatus.RUNNING);
        assertThat(partial.targets()).extracting(CacheInvalidationTarget::status)
                .containsExactly(CacheTargetStatus.PURGED, CacheTargetStatus.PENDING);
        assertThat(partial.failureReason()).isEmpty();
    }

    @Test
    void thirdFailureExhaustsBudgetAndFourthAttemptIsImpossible() {
        CacheInvalidationBatch batch = requestedBatch();
        for (int number = 1; number <= 3; number++) {
            if (number == 1) batch.beginInitialAttempt(); else batch.beginManualRetry();
            Instant attempted = ATTEMPTED_AT.plusSeconds(number);
            batch.recordAttemptResult(number, CachePurgeProvider.CLOUDFLARE, attempted, CachePurgeResult.FAILED,
                    CachePurgeFailureReason.PROVIDER_5XX,
                    pendingOutcomes(batch, CacheTargetStatus.FAILED), attempted.plusSeconds(1));
        }

        assertThat(batch.attempts()).extracting(CachePurgeAttempt::attemptNumber).containsExactly(1, 2, 3);
        assertThat(batch.completedAt()).isPresent();
        assertThatThrownBy(batch::beginManualRetry).isInstanceOf(CacheInvalidationValidationException.class);
    }

    @Test
    void partialFailureCanSucceedOnManualRetryWithoutOverwritingHistory() {
        CacheInvalidationBatch batch = requestedBatch();
        batch.beginInitialAttempt();
        batch.recordAttemptResult(1, CachePurgeProvider.CLOUDFLARE, ATTEMPTED_AT, CachePurgeResult.FAILED,
                CachePurgeFailureReason.RATE_LIMITED,
                List.of(outcome(batch, 0, CacheTargetStatus.PURGED), outcome(batch, 1, CacheTargetStatus.FAILED)),
                RECORDED_AT);
        CacheInvalidationTargetId satisfiedId = batch.targets().getFirst().id();
        batch.beginManualRetry();
        batch.recordAttemptResult(2, CachePurgeProvider.CLOUDFLARE, RECORDED_AT.plusSeconds(1),
                CachePurgeResult.SUCCESS, null, pendingOutcomes(batch, CacheTargetStatus.PURGED),
                RECORDED_AT.plusSeconds(2));

        assertThat(batch.status()).isEqualTo(CacheInvalidationStatus.COMPLETED);
        assertThat(batch.targets().getFirst().id()).isEqualTo(satisfiedId);
        assertThat(batch.attempts()).extracting(CachePurgeAttempt::result)
                .containsExactly(CachePurgeResult.FAILED, CachePurgeResult.SUCCESS);
        assertThat(batch.failureReason()).isEmpty();
    }

    @Test
    void resultRequiresExactPendingCoverageAndConsistentResult() {
        CacheInvalidationBatch batch = requestedBatch();
        batch.beginInitialAttempt();
        CacheTargetOutcome first = outcome(batch, 0, CacheTargetStatus.PURGED);
        CacheTargetOutcome second = outcome(batch, 1, CacheTargetStatus.PURGED);

        assertThatThrownBy(() -> batch.recordAttemptResult(1, CachePurgeProvider.LOCAL, ATTEMPTED_AT,
                CachePurgeResult.SUCCESS, null, List.of(first), RECORDED_AT))
                .isInstanceOf(CacheInvalidationValidationException.class);
        assertThatThrownBy(() -> batch.recordAttemptResult(1, CachePurgeProvider.LOCAL, ATTEMPTED_AT,
                CachePurgeResult.SUCCESS, null, List.of(first, first), RECORDED_AT))
                .isInstanceOf(CacheInvalidationValidationException.class);
        assertThatThrownBy(() -> batch.recordAttemptResult(1, CachePurgeProvider.LOCAL, ATTEMPTED_AT,
                CachePurgeResult.SUCCESS, null,
                List.of(first, CacheTargetOutcome.of(CacheInvalidationTargetId.newId(), CacheTargetStatus.PURGED)),
                RECORDED_AT)).isInstanceOf(CacheInvalidationValidationException.class);
        assertThatThrownBy(() -> batch.recordAttemptResult(2, CachePurgeProvider.LOCAL, ATTEMPTED_AT,
                CachePurgeResult.SUCCESS, null, List.of(first, second), RECORDED_AT))
                .isInstanceOf(CacheInvalidationValidationException.class);
        assertThatThrownBy(() -> batch.recordAttemptResult(1, CachePurgeProvider.LOCAL,
                REQUESTED_AT.minusSeconds(1), CachePurgeResult.SUCCESS, null, List.of(first, second), RECORDED_AT))
                .isInstanceOf(CacheInvalidationValidationException.class);
        assertThatThrownBy(() -> batch.recordAttemptResult(1, CachePurgeProvider.LOCAL, ATTEMPTED_AT,
                CachePurgeResult.SUCCESS, CachePurgeFailureReason.TIMEOUT, List.of(first, second), RECORDED_AT))
                .isInstanceOf(CacheInvalidationValidationException.class);
        assertThatThrownBy(() -> CacheTargetOutcome.of(batch.targets().getFirst().id(), CacheTargetStatus.PENDING))
                .isInstanceOf(CacheInvalidationValidationException.class);
    }

    @Test
    void rehydrationRejectsCorruptStructuralLifecycleAndTemporalState() {
        CacheInvalidationBatch valid = requestedBatch();
        assertInvalidRehydration(List.of(), List.of(), CacheInvalidationStatus.REQUESTED, null, null);
        assertInvalidRehydration(List.of(valid.targets().getFirst(), valid.targets().getFirst()), List.of(),
                CacheInvalidationStatus.REQUESTED, null, null);

        List<CachePurgeAttempt> nonContiguous = List.of(attempt(2, ATTEMPTED_AT, CachePurgeResult.FAILED,
                CachePurgeFailureReason.TIMEOUT));
        assertInvalidRehydration(failedTargets(valid), nonContiguous, CacheInvalidationStatus.FAILED, null,
                CachePurgeFailureReason.TIMEOUT);

        List<CachePurgeAttempt> four = new ArrayList<>();
        for (int i = 1; i <= 3; i++) four.add(attempt(i, ATTEMPTED_AT.plusSeconds(i), CachePurgeResult.FAILED,
                CachePurgeFailureReason.TIMEOUT));
        four.add(new CachePurgeAttempt(CachePurgeAttemptId.newId(), 3, CachePurgeProvider.LOCAL,
                ATTEMPTED_AT.plusSeconds(4), CachePurgeResult.FAILED, CachePurgeFailureReason.TIMEOUT));
        assertInvalidRehydration(failedTargets(valid), four, CacheInvalidationStatus.FAILED, RECORDED_AT.plusSeconds(10),
                CachePurgeFailureReason.TIMEOUT);

        assertInvalidRehydration(valid.targets(), List.of(attempt(1, ATTEMPTED_AT, CachePurgeResult.FAILED,
                        CachePurgeFailureReason.TIMEOUT)), CacheInvalidationStatus.REQUESTED, null, null);
        assertThatThrownBy(() -> new CachePurgeAttempt(CachePurgeAttemptId.newId(), 1, CachePurgeProvider.LOCAL,
                ATTEMPTED_AT, CachePurgeResult.SUCCESS, CachePurgeFailureReason.TIMEOUT))
                .isInstanceOf(CacheInvalidationValidationException.class);
        assertThatThrownBy(() -> new CachePurgeAttempt(CachePurgeAttemptId.newId(), 1, CachePurgeProvider.LOCAL,
                ATTEMPTED_AT, CachePurgeResult.FAILED, null))
                .isInstanceOf(CacheInvalidationValidationException.class);
        assertInvalidRehydration(failedTargets(valid), List.of(attempt(1, ATTEMPTED_AT, CachePurgeResult.FAILED,
                        CachePurgeFailureReason.TIMEOUT)), CacheInvalidationStatus.COMPLETED, RECORDED_AT, null);
        assertInvalidRehydration(failedTargets(valid), List.of(attempt(1, ATTEMPTED_AT, CachePurgeResult.FAILED,
                        CachePurgeFailureReason.TIMEOUT)), CacheInvalidationStatus.FAILED, null, null);
        assertInvalidRehydration(failedTargets(valid), List.of(attempt(1, ATTEMPTED_AT, CachePurgeResult.FAILED,
                        CachePurgeFailureReason.TIMEOUT)), CacheInvalidationStatus.FAILED, RECORDED_AT,
                CachePurgeFailureReason.TIMEOUT);
        assertInvalidRehydration(failedTargets(valid), List.of(
                        attempt(1, ATTEMPTED_AT, CachePurgeResult.FAILED, CachePurgeFailureReason.TIMEOUT),
                        attempt(2, ATTEMPTED_AT.plusSeconds(1), CachePurgeResult.FAILED, CachePurgeFailureReason.TIMEOUT),
                        attempt(3, ATTEMPTED_AT.plusSeconds(2), CachePurgeResult.FAILED, CachePurgeFailureReason.TIMEOUT)),
                CacheInvalidationStatus.FAILED, null, CachePurgeFailureReason.TIMEOUT);
        assertInvalidRehydration(failedTargets(valid), List.of(
                        attempt(1, ATTEMPTED_AT, CachePurgeResult.FAILED, CachePurgeFailureReason.TIMEOUT),
                        attempt(2, ATTEMPTED_AT.minusSeconds(1), CachePurgeResult.FAILED, CachePurgeFailureReason.TIMEOUT)),
                CacheInvalidationStatus.FAILED, null, CachePurgeFailureReason.TIMEOUT);
    }

    private static CacheInvalidationBatch requestedBatch() {
        return CacheInvalidationBatch.request(CacheInvalidationBatchId.newId(),
                InvalidationReason.PUBLIC_RESOURCE_CHANGED, InvalidationRequester.SYSTEM, REQUESTED_AT,
                List.of(url("/articles/example"), tag("site:public-documents")));
    }
    private static CacheInvalidationTarget url(String value) {
        return CacheInvalidationTarget.pending(CacheInvalidationTargetId.newId(), CacheTargetType.URL,
                CacheTargetValue.url(value));
    }
    private static CacheInvalidationTarget tag(String value) {
        return CacheInvalidationTarget.pending(CacheInvalidationTargetId.newId(), CacheTargetType.CACHE_TAG,
                CacheTargetValue.cacheTag(value));
    }
    private static CacheTargetOutcome outcome(CacheInvalidationBatch batch, int index, CacheTargetStatus status) {
        return CacheTargetOutcome.of(batch.targets().get(index).id(), status);
    }
    private static List<CacheTargetOutcome> pendingOutcomes(CacheInvalidationBatch batch, CacheTargetStatus status) {
        return batch.targets().stream().filter(target -> target.status() == CacheTargetStatus.PENDING)
                .map(target -> CacheTargetOutcome.of(target.id(), status)).toList();
    }
    private static List<CacheInvalidationTarget> failedTargets(CacheInvalidationBatch batch) {
        return batch.targets().stream().map(target -> CacheInvalidationTarget.rehydrate(target.id(), target.targetType(),
                target.value(), CacheTargetStatus.FAILED)).toList();
    }
    private static CachePurgeAttempt attempt(int number, Instant at, CachePurgeResult result,
            CachePurgeFailureReason failureReason) {
        return new CachePurgeAttempt(CachePurgeAttemptId.newId(), number, CachePurgeProvider.LOCAL, at, result,
                failureReason);
    }
    private static void assertInvalidRehydration(List<CacheInvalidationTarget> targets,
            List<CachePurgeAttempt> attempts, CacheInvalidationStatus status, Instant completedAt,
            CachePurgeFailureReason reason) {
        assertThatThrownBy(() -> CacheInvalidationBatch.rehydrate(CacheInvalidationBatchId.from(UUID.randomUUID()),
                InvalidationReason.PUBLIC_RESOURCE_CHANGED, InvalidationRequester.SYSTEM, REQUESTED_AT, status,
                targets, attempts, completedAt, reason, 0)).isInstanceOf(CacheInvalidationValidationException.class);
    }
}
