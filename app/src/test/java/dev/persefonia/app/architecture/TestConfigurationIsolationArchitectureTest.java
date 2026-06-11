package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.PersefoniaApplication;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;

class TestConfigurationIsolationArchitectureTest {
    @Test
    void applicationScanPreservesBootAndManagementContextExclusions() {
        ComponentScan scan = PersefoniaApplication.class.getAnnotation(ComponentScan.class);

        assertThat(scan).isNotNull();
        assertThat(hasFilter(scan, FilterType.CUSTOM, TypeExcludeFilter.class)).isTrue();
        assertThat(hasFilter(scan, FilterType.CUSTOM, AutoConfigurationExcludeFilter.class)).isTrue();
        assertThat(hasFilter(scan, FilterType.ANNOTATION, ManagementContextConfiguration.class)).isTrue();
    }

    @Test
    void adminContentTestConfigurationRequiresNarrowProfile() throws ClassNotFoundException {
        Class<?> configuration =
                Class.forName("dev.persefonia.app.webadmin.content.AdminContentTestConfiguration");

        assertThat(configuration.getAnnotation(Profile.class).value())
                .containsExactly("admin-content-mvc-test");
    }

    private static boolean hasFilter(ComponentScan scan, FilterType type, Class<?> filteredClass) {
        return Arrays.stream(scan.excludeFilters())
                .anyMatch(filter -> filter.type() == type && Arrays.asList(filter.classes()).contains(filteredClass));
    }
}
