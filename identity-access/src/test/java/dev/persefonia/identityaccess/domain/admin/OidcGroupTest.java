package dev.persefonia.identityaccess.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OidcGroupTest {
    @Test
    void trimsAndPreservesCaseAndInternalWhitespace() {
        assertThat(OidcGroup.of("admin").value()).isEqualTo("admin");
        assertThat(OidcGroup.of("  Admin group  ").value()).isEqualTo("Admin group");
    }

    @Test
    void rejectsNullBlankControlCharactersAndExcessiveLength() {
        assertThatThrownBy(() -> OidcGroup.of(null)).isInstanceOf(NullPointerException.class);
        for (String value : new String[] {"", "   ", "admin\n", "ad\u007fmin", "a".repeat(129)}) {
            assertThatThrownBy(() -> OidcGroup.of(value)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(OidcGroup.of("a".repeat(128)).value()).hasSize(128);
    }
}
