package dev.persefonia.app.webadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.persefonia.identityaccess.domain.admin.AdminAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.web.context.SecurityContextRepository;

class AdminBrowserPreviewIsolationTest {
    @Test
    void fixtureRequiresExplicitTestConfigurationAndNarrowProfile() {
        var configuration = AdminBrowserPreview.PreviewConfiguration.class;
        assertThat(configuration.isAnnotationPresent(TestConfiguration.class)).isTrue();
        assertThat(configuration.getAnnotation(Profile.class).value()).containsExactly("admin-browser-preview");
    }

    @Test
    void fixtureRejectsNonLoopbackNonDisposableProductionAndForeignDatabaseLaunches() {
        for (String violation : java.util.List.of("bind", "disposable", "profile", "database")) {
            var environment = new MockEnvironment()
                    .withProperty("server.address", "127.0.0.1")
                    .withProperty("persefonia.preview.disposable", "true")
                    .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:12345/persefonia_browser");
            switch (violation) {
                case "bind" -> environment.setProperty("server.address", "0.0.0.0");
                case "disposable" -> environment.setProperty("persefonia.preview.disposable", "false");
                case "profile" -> environment.setActiveProfiles("production");
                case "database" -> environment.setProperty("spring.datasource.url", "jdbc:postgresql://remote/real-data");
                default -> throw new AssertionError(violation);
            }
            assertThatThrownBy(() -> new AdminBrowserPreview.PreviewConfiguration().syntheticPreviewSession(
                    environment, mock(AdminAccountRepository.class), mock(SecurityContextRepository.class)))
                    .as(violation).isInstanceOf(IllegalStateException.class);
        }
    }
}
