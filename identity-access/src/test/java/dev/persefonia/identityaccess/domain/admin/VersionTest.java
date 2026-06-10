package dev.persefonia.identityaccess.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VersionTest {
    @Test
    void initialIsZero() {
        assertThat(Version.initial().value()).isZero();
    }

    @Test
    void rejectsNegative() {
        assertThatThrownBy(() -> Version.of(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nextIncrements() {
        assertThat(Version.of(4).next()).isEqualTo(Version.of(5));
    }
}
