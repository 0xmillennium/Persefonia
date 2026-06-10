package dev.persefonia.identityaccess.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OidcSubjectTest {
    @Test
    void acceptsOpaqueSubject() {
        assertThat(OidcSubject.of("https://issuer.example/users/subject:42").value())
                .isEqualTo("https://issuer.example/users/subject:42");
    }

    @Test
    void preservesCase() {
        assertThat(OidcSubject.of("OpaqueSUBJECT").value()).isEqualTo("OpaqueSUBJECT");
    }

    @Test
    void trimsOuterWhitespace() {
        assertThat(OidcSubject.of("  opaque subject  ").value()).isEqualTo("opaque subject");
    }

    @Test
    void rejectsBlank() {
        assertInvalid(" ");
    }

    @Test
    void rejectsControlCharacters() {
        assertInvalid("subject\nvalue");
    }

    @Test
    void rejectsTooLongValue() {
        assertInvalid("a".repeat(513));
    }

    private static void assertInvalid(String value) {
        assertThatThrownBy(() -> OidcSubject.of(value))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
