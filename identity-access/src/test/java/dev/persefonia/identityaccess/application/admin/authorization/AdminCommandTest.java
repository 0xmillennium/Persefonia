package dev.persefonia.identityaccess.application.admin.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class AdminCommandTest {
    @Test
    void createsNamedCommand() {
        assertThat(AdminCommand.named("content.publish").name()).isEqualTo("content.publish");
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

    @ParameterizedTest
    @ValueSource(strings = {
            "content.publish",
            "content:publish",
            "project.archive",
            "media_upload",
            "test.admin.mutate",
            "x",
            "x-1",
            "x_1",
            "x:1",
            "x.1"
    })
    void acceptsStrictCommandNames(String commandName) {
        assertThat(AdminCommand.named(commandName).name()).isEqualTo(commandName);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Content.Publish",
            "content publish",
            "../content.publish",
            "content/publish",
            " content.publish",
            "content.publish ",
            ".content",
            "-content",
            "_content",
            ":content",
            "content*",
            "content?"
    })
    void rejectsInvalidCommandNames(String commandName) {
        assertThatThrownBy(() -> AdminCommand.named(commandName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("match");
    }
}
