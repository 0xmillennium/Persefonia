package dev.persefonia.identityaccess.application.admin.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AdminCommandTest {
    @Test
    void createsNamedCommand() {
        assertThat(AdminCommand.named("content.publish").name()).isEqualTo("content.publish");
    }

    @Test
    void trimsName() {
        assertThat(AdminCommand.named(" content.publish ").name()).isEqualTo("content.publish");
    }

    @Test
    void rejectsNullName() {
        assertThatThrownBy(() -> AdminCommand.named(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> AdminCommand.named("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsControlCharacters() {
        assertThatThrownBy(() -> AdminCommand.named("content.\npublish"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control");
    }

    @Test
    void rejectsTooLongName() {
        assertThatThrownBy(() -> AdminCommand.named("a".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("128");
    }

    @Test
    void acceptsDotDashUnderscoreColon() {
        assertThat(AdminCommand.named("content.publish-draft_v2:owner").name())
                .isEqualTo("content.publish-draft_v2:owner");
    }
}
