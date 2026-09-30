package dev.persefonia.app.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.profileportfolio.application.service.StandardPublicThemeQueryService;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.settings.HomepagePromotionLimit;
import dev.persefonia.profileportfolio.domain.settings.HomepageSettings;
import dev.persefonia.profileportfolio.domain.settings.SiteName;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsId;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
import dev.persefonia.webpublic.StandardPublicThemeController;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class StandardPublicThemeControllerTest {
    @Test
    void emitsLightDarkAndSystemTokenRulesWithoutJavascript() {
        var settings = SitePresentationSettings.create(SitePresentationSettingsId.newId(), SiteName.of("Persefonia"),
                ContentLanguage.EN, Set.of(ContentLanguage.EN), null, null, null, ThemePreference.LIGHT,
                HomepageSettings.of(true, true, true, HomepagePromotionLimit.of(3),
                        HomepagePromotionLimit.of(3), HomepagePromotionLimit.of(3)), Instant.now());
        SitePresentationSettingsRepository repository = new SitePresentationSettingsRepository() {
            @Override public SitePresentationSettings save(SitePresentationSettings value) { return value; }
            @Override public Optional<SitePresentationSettings> findCurrent() { return Optional.of(settings); }
            @Override public Optional<SitePresentationSettings> findById(SitePresentationSettingsId id) {
                return Optional.of(settings).filter(value -> value.id().equals(id));
            }
        };
        var controller = new StandardPublicThemeController(
                new StandardPublicThemeQueryService(repository), new PublicContentResponseHeaders());
        var response = new MockHttpServletResponse();
        assertThat(controller.stylesheet(response)).contains("color-scheme:light").doesNotContain("@media");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("public, no-cache, must-revalidate");
        settings.changeTheme(ThemePreference.DARK, Instant.now());
        assertThat(controller.stylesheet(new MockHttpServletResponse()))
                .contains("color-scheme:dark", "--page-bg:#10161b").doesNotContain("@media");
        settings.changeTheme(ThemePreference.SYSTEM, Instant.now());
        assertThat(controller.stylesheet(new MockHttpServletResponse()))
                .contains("color-scheme:light", "@media (prefers-color-scheme:dark)", "color-scheme:dark");
    }
}
