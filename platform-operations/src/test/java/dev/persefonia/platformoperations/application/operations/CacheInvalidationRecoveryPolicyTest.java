package dev.persefonia.platformoperations.application.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CacheInvalidationRecoveryPolicyTest {
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private final CacheInvalidationRecoveryPolicy policy =
            new CacheInvalidationRecoveryPolicy(Duration.ofMinutes(15));

    @Test
    void mapsEveryDurableStateToItsAttentionAndRecoveryAction() {
        assertState(CacheInvalidationStatus.REQUESTED, null, null, 0,
                CacheInvalidationAttentionState.PENDING_INITIAL, CacheRecoveryAction.EXECUTE_INITIAL);
        assertState(CacheInvalidationStatus.RUNNING, NOW.minusSeconds(899), null, 0,
                CacheInvalidationAttentionState.RUNNING, CacheRecoveryAction.NONE);
        assertState(CacheInvalidationStatus.FAILED, null, null, 1,
                CacheInvalidationAttentionState.RETRY_AVAILABLE, CacheRecoveryAction.RETRY_FAILED);
        assertState(CacheInvalidationStatus.PARTIAL, null, NOW, 3,
                CacheInvalidationAttentionState.RETRY_EXHAUSTED, CacheRecoveryAction.NONE);
        assertState(CacheInvalidationStatus.COMPLETED, null, NOW, 1,
                CacheInvalidationAttentionState.COMPLETED, CacheRecoveryAction.NONE);
    }

    @Test
    void strandedBoundaryIsInclusiveToTheNanosecond() {
        Instant cutoff = NOW.minus(Duration.ofMinutes(15));
        assertThat(policy.attention(CacheInvalidationStatus.RUNNING, cutoff.minusNanos(1), null, 0, NOW))
                .isEqualTo(CacheInvalidationAttentionState.STRANDED);
        assertThat(policy.attention(CacheInvalidationStatus.RUNNING, cutoff, null, 0, NOW))
                .isEqualTo(CacheInvalidationAttentionState.STRANDED);
        assertThat(policy.attention(CacheInvalidationStatus.RUNNING, cutoff.plusNanos(1), null, 0, NOW))
                .isEqualTo(CacheInvalidationAttentionState.RUNNING);
    }

    @Test
    void thresholdIsBounded() {
        assertThatThrownBy(() -> new CacheInvalidationRecoveryPolicy(Duration.ofSeconds(59)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CacheInvalidationRecoveryPolicy(Duration.ofHours(24).plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertState(CacheInvalidationStatus status, Instant runningSince, Instant completedAt,
            int attempts, CacheInvalidationAttentionState attention, CacheRecoveryAction action) {
        assertThat(policy.attention(status, runningSince, completedAt, attempts, NOW)).isEqualTo(attention);
        assertThat(policy.action(status, runningSince, completedAt, attempts, NOW)).isEqualTo(action);
    }
}
