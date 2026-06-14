package dev.persefonia.discovery.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DiscoveryIdentityValueTest {
    @Test
    void aggregateIdentitiesRejectNull() {
        assertThatThrownBy(() -> new DiscoverableResourceId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RedirectRuleId(null)).isInstanceOf(NullPointerException.class);
    }
}
