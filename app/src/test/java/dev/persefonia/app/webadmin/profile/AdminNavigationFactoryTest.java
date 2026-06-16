package dev.persefonia.app.webadmin.profile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.webadmin.AdminNavigationFactory;
import dev.persefonia.webadmin.AdminNavigationSection;
import org.junit.jupiter.api.Test;

class AdminNavigationFactoryTest {
    @Test
    void enablesSettingsAndProfileWhileProjectsRemainDisabled() {
        var navigation = new AdminNavigationFactory().create(AdminNavigationSection.PROFILE);

        assertThat(navigation).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("Profile");
            assertThat(item.active()).isTrue();
            assertThat(item.disabled()).isFalse();
            assertThat(item.href()).isEqualTo("/admin/profile");
        });
        assertThat(navigation).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("Settings");
            assertThat(item.disabled()).isFalse();
            assertThat(item.href()).isEqualTo("/admin/settings/site");
        });
        assertThat(navigation).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("Projects");
            assertThat(item.disabled()).isTrue();
        });
    }
}
