package dev.persefonia.identityaccess.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DisplayNameTest {
    @Test
    void acceptsNormalDisplayName() {
        assertThat(DisplayName.of("Persefonia Owner").value()).isEqualTo("Persefonia Owner");
    }

    @Test
    void trimsOuterWhitespace() {
        assertThat(DisplayName.of("  Persefonia Owner  ").value()).isEqualTo("Persefonia Owner");
    }

    @Test
    void rejectsBlank() {
        assertInvalid(" ");
    }

    @Test
    void rejectsControlCharacters() {
        assertInvalid("Owner\nName");
    }

    @Test
    void rejectsTooLongValue() {
        assertInvalid("a".repeat(201));
    }

    private static void assertInvalid(String value) {
        assertThatThrownBy(() -> DisplayName.of(value))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
