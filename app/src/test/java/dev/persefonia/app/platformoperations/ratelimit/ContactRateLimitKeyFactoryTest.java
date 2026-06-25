package dev.persefonia.app.platformoperations.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.platformoperations.application.port.RateLimitScope;
import org.junit.jupiter.api.Test;

class ContactRateLimitKeyFactoryTest {
    @Test
    void sameInputProducesSameDerivedKey() {
        var factory = new ContactRateLimitKeyFactory("secret-one");

        assertThat(factory.derive(RateLimitScope.CONTACT_FORM_SUBMISSION, "203.0.113.10"))
                .isEqualTo(factory.derive(RateLimitScope.CONTACT_FORM_SUBMISSION, "203.0.113.10"));
    }

    @Test
    void differentSecretSignalOrScopeChangesKey() {
        var first = new ContactRateLimitKeyFactory("secret-one");
        var second = new ContactRateLimitKeyFactory("secret-two");

        String base = first.derive(RateLimitScope.CONTACT_FORM_SUBMISSION, "203.0.113.10").value();

        assertThat(second.derive(RateLimitScope.CONTACT_FORM_SUBMISSION, "203.0.113.10").value()).isNotEqualTo(base);
        assertThat(first.derive(RateLimitScope.CONTACT_FORM_SUBMISSION, "203.0.113.11").value()).isNotEqualTo(base);
        assertThat(first.derive(TestScope.SECOND_SCOPE, "203.0.113.10").value()).isNotEqualTo(base);
    }

    @Test
    void derivedKeyDoesNotContainRawSignalOrSecret() {
        var factory = new ContactRateLimitKeyFactory("very-secret-value");

        String key = factory.derive(RateLimitScope.CONTACT_FORM_SUBMISSION, "203.0.113.10").value();

        assertThat(key).startsWith("contact-form:");
        assertThat(key)
                .doesNotContain("203.0.113.10")
                .doesNotContain("very-secret-value");
    }

    @Test
    void rejectsBlankInputs() {
        assertThatThrownBy(() -> new ContactRateLimitKeyFactory(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContactRateLimitKeyFactory("secret").derive(
                RateLimitScope.CONTACT_FORM_SUBMISSION,
                " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private enum TestScope {
        SECOND_SCOPE
    }
}
