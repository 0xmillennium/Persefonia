package dev.persefonia.discovery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DiscoveryTextValueTest {
    @Test
    void requiredTextValuesRejectBlankAndNormalizeOuterWhitespace() {
        assertThatThrownBy(() -> new ResourceTitle(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResourceSummary("\t")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchText("\n")).isInstanceOf(IllegalArgumentException.class);

        assertThat(new ResourceTitle(" Title ").value()).isEqualTo("Title");
        assertThat(new ResourceSummary(" Summary ").value()).isEqualTo("Summary");
        assertThat(new SearchText(" Search text ").value()).isEqualTo("Search text");
    }

    @Test
    void optionalOpenGraphValuesRejectBlankWhenPresent() {
        assertThatThrownBy(() -> new OpenGraphTitle(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpenGraphDescription("\t")).isInstanceOf(IllegalArgumentException.class);
        assertThat(SocialPreviewProfile.empty())
                .isEqualTo(new SocialPreviewProfile(null, null, null));
    }
}
