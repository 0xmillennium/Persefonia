package dev.persefonia.app.webadmin.profile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.webadmin.AdminNavigationFactory;
import dev.persefonia.webadmin.AdminNavigationSection;
import org.junit.jupiter.api.Test;

class AdminNavigationFactoryTest {
    @Test
    void enablesSettingsProfileProjectsCvMediaAndContact() {
        var navigation = new AdminNavigationFactory().create(AdminNavigationSection.PROJECTS);

        assertThat(navigation).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("Profile");
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
            assertThat(item.active()).isTrue();
            assertThat(item.disabled()).isFalse();
            assertThat(item.href()).isEqualTo("/admin/projects");
        });
        assertThat(navigation).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("CV");
            assertThat(item.disabled()).isFalse();
            assertThat(item.href()).isEqualTo("/admin/cv");
        });
        assertThat(navigation).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("Media");
            assertThat(item.disabled()).isFalse();
            assertThat(item.href()).isEqualTo("/admin/media");
        });
        assertThat(navigation).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("Contact");
            assertThat(item.disabled()).isFalse();
            assertThat(item.href()).isEqualTo("/admin/contact");
        });
    }

    @Test
    void mediaNavigationIsActiveForMediaPages() {
        var navigation = new AdminNavigationFactory().create(AdminNavigationSection.MEDIA);

        assertThat(navigation).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("Media");
            assertThat(item.active()).isTrue();
            assertThat(item.href()).isEqualTo("/admin/media");
        });
    }

    @Test
    void cvNavigationIsActiveForCvPages() {
        var navigation = new AdminNavigationFactory().create(AdminNavigationSection.CV);

        assertThat(navigation).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("CV");
            assertThat(item.active()).isTrue();
            assertThat(item.href()).isEqualTo("/admin/cv");
        });
    }

    @Test
    void contactNavigationIsActiveForContactPages() {
        var navigation = new AdminNavigationFactory().create(AdminNavigationSection.CONTACT);

        assertThat(navigation).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("Contact");
            assertThat(item.active()).isTrue();
            assertThat(item.href()).isEqualTo("/admin/contact");
        });
    }
}
