package dev.persefonia.app.platformoperations.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.platformoperations.application.port.RateLimitKey;
import dev.persefonia.platformoperations.application.port.RateLimitRejectionReason;
import dev.persefonia.platformoperations.application.port.RateLimitRequest;
import dev.persefonia.platformoperations.application.port.RateLimitScope;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedisRateLimitPortAdapterTest {
    private static final RateLimitRequest REQUEST = new RateLimitRequest(
            RateLimitScope.CONTACT_FORM_SUBMISSION,
            new RateLimitKey("contact-form:derived"),
            3,
            Duration.ofMinutes(10));

    private final FakeRedisCounterStore counters = new FakeRedisCounterStore();
    private final RedisRateLimitPortAdapter adapter =
            new RedisRateLimitPortAdapter(counters, "persefonia:rate-limit");

    @Test
    void allowsRequestsUpToConfiguredMaximumAndSetsTtlOnFirstRequest() {
        assertThat(adapter.checkAndConsume(REQUEST).allowed()).isTrue();
        assertThat(adapter.checkAndConsume(REQUEST).allowed()).isTrue();
        var third = adapter.checkAndConsume(REQUEST);

        assertThat(third.allowed()).isTrue();
        assertThat(third.remainingAttempts()).isZero();
        assertThat(counters.expirations()).containsExactly(Duration.ofMinutes(10));
    }

    @Test
    void rejectsRequestAboveMaximum() {
        adapter.checkAndConsume(REQUEST);
        adapter.checkAndConsume(REQUEST);
        adapter.checkAndConsume(REQUEST);

        var rejected = adapter.checkAndConsume(REQUEST);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.rejectionReason()).isEqualTo(RateLimitRejectionReason.LIMIT_EXCEEDED);
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void usesNamespacedRedisKey() {
        adapter.checkAndConsume(REQUEST);

        assertThat(counters.keys()).containsExactly(
                "persefonia:rate-limit:contact_form_submission:contact-form:derived");
    }

    @Test
    void redisExceptionMapsToUnavailableDecision() {
        counters.fail = true;

        var decision = adapter.checkAndConsume(REQUEST);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.rejectionReason()).isEqualTo(RateLimitRejectionReason.TEMPORARILY_UNAVAILABLE);
    }

    private static final class FakeRedisCounterStore implements RedisCounterStore {
        private final List<String> keys = new ArrayList<>();
        private final List<Duration> expirations = new ArrayList<>();
        private long value;
        private boolean fail;

        @Override
        public long increment(String key) {
            if (fail) {
                throw new IllegalStateException("unavailable");
            }
            keys.add(key);
            return ++value;
        }

        @Override
        public void expire(String key, Duration window) {
            if (fail) {
                throw new IllegalStateException("unavailable");
            }
            expirations.add(window);
        }

        List<String> keys() {
            return keys;
        }

        List<Duration> expirations() {
            return expirations;
        }
    }
}
