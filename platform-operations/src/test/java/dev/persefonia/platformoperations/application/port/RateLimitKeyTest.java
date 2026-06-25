package dev.persefonia.platformoperations.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RateLimitKeyTest {
    @Test
    void normalizesAlreadyDerivedKey() {
        assertThat(new RateLimitKey(" contact-form:abc123 ").value()).isEqualTo("contact-form:abc123");
    }

    @Test
    void rejectsBlankOrControlCharacterKeys() {
        assertThatThrownBy(() -> new RateLimitKey(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitKey("derived\nkey"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
