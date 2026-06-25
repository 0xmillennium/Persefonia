package dev.persefonia.platformoperations.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitDecisionTest {
    @Test
    void constructsAllowedAndRejectedDecisions() {
        assertThat(RateLimitDecision.allowed(3).allowed()).isTrue();

        var rejected = RateLimitDecision.rejected(
                RateLimitRejectionReason.LIMIT_EXCEEDED,
                Duration.ofMinutes(5));

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.rejectionReason()).isEqualTo(RateLimitRejectionReason.LIMIT_EXCEEDED);
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void rejectsInvalidDecisionCombinations() {
        assertThatThrownBy(() -> new RateLimitDecision(true, RateLimitRejectionReason.LIMIT_EXCEEDED, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitDecision(false, null, null, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RateLimitDecision.allowed(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
