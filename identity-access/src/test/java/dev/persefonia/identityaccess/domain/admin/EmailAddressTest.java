package dev.persefonia.identityaccess.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class EmailAddressTest {
    @Test
    void acceptsSimpleEmail() {
        assertThat(EmailAddress.of("owner@example.com").value()).isEqualTo("owner@example.com");
    }

    @Test
    void trimsOuterWhitespace() {
        assertThat(EmailAddress.of("  owner@example.com  ").value()).isEqualTo("owner@example.com");
    }

    @Test
    void rejectsBlank() {
        assertInvalid(" ");
    }

    @Test
    void rejectsMissingAt() {
        assertInvalid("owner.example.com");
    }

    @Test
    void rejectsMultipleAt() {
        assertInvalid("owner@example@com");
    }

    @Test
    void rejectsMissingLocalPart() {
        assertInvalid("@example.com");
    }

    @Test
    void rejectsMissingDomainPart() {
        assertInvalid("owner@");
    }

    @Test
    void rejectsDomainWithoutDot() {
        assertInvalid("owner@example");
    }

    @Test
    void rejectsWhitespace() {
        assertInvalid("own er@example.com");
    }

    @Test
    void rejectsControlCharacters() {
        assertInvalid("owner@example.com\n");
    }

    @Test
    void normalizedEmailLowercasesWithLocaleRoot() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(NormalizedEmailAddress.from(EmailAddress.of("I@EXAMPLE.COM")).value())
                    .isEqualTo("i@example.com");
        } finally {
            Locale.setDefault(original);
        }
    }

    private static void assertInvalid(String value) {
        assertThatThrownBy(() -> EmailAddress.of(value))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
