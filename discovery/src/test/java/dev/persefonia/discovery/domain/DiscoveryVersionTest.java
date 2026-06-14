package dev.persefonia.discovery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DiscoveryVersionTest {
    @Test
    void versionStartsAtZeroAndAdvances() {
        assertThat(Version.initial()).isEqualTo(new Version(0));
        assertThat(Version.initial().next()).isEqualTo(new Version(1));
    }

    @Test
    void versionRejectsNegativeValue() {
        assertThatThrownBy(() -> new Version(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
