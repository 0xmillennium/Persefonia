package dev.persefonia.platformoperations.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitRequestTest {
    @Test
    void acceptsPositiveLimitAndWindow() {
        var request = new RateLimitRequest(
                RateLimitScope.CONTACT_FORM_SUBMISSION,
                new RateLimitKey("contact-form:derived"),
                5,
                Duration.ofMinutes(1));

        assertThat(request.maxAttempts()).isEqualTo(5);
        assertThat(request.window()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void rejectsInvalidLimitAndWindow() {
        var key = new RateLimitKey("contact-form:derived");
        assertThatThrownBy(() -> new RateLimitRequest(RateLimitScope.CONTACT_FORM_SUBMISSION, key, 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitRequest(RateLimitScope.CONTACT_FORM_SUBMISSION, key, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
