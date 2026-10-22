package dev.persefonia.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.persefonia.app.TestPortfolioSettingsFallbackConfiguration;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsId;
import dev.persefonia.profileportfolio.domain.settings.SiteName;
import dev.persefonia.profileportfolio.domain.settings.TitleSuffix;
import dev.persefonia.profileportfolio.domain.settings.SeoDescription;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
import dev.persefonia.profileportfolio.domain.settings.HomepageSettings;
import dev.persefonia.profileportfolio.domain.settings.HomepagePromotionLimit;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(TestPortfolioSettingsFallbackConfiguration.class)
class PublicDocumentLanguageTest {
    @Autowired MockMvc mvc;
    @MockitoBean(name = "testPortfolioSettingsRepository") SitePresentationSettingsRepository settings;

    @MockitoBean dev.persefonia.profileportfolio.application.service.PublicProjectListingQueryService projects;
    @MockitoBean dev.persefonia.profileportfolio.application.service.ActiveCvPublicQueryService cv;

    @ParameterizedTest
    @EnumSource(ContentLanguage.class)
    void representationLanguageWinsOverRequestPreference(ContentLanguage language) throws Exception {
        when(projects.list(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of());
        when(settings.findCurrent()).thenReturn(Optional.of(SitePresentationSettings.create(
                SitePresentationSettingsId.newId(), SiteName.of("Language test"), language,
                Set.of(ContentLanguage.EN, ContentLanguage.TR), TitleSuffix.of("| Home"),
                SeoDescription.of("Language test"), null, ThemePreference.SYSTEM,
                HomepageSettings.of(false, false, false, HomepagePromotionLimit.of(3),
                        HomepagePromotionLimit.of(3), HomepagePromotionLimit.of(3)), Instant.EPOCH)));
        for (String preference : java.util.List.of("en-US", "tr-TR")) {
            assertLanguage("/", preference, language.name().toLowerCase(java.util.Locale.ROOT), 200);
            for (String path : java.util.List.of("/search", "/contact", "/en/projects")) {
                assertLanguage(path, preference, "en", 200);
            }
            assertLanguage("/tr/projects", preference, "tr", 200);
            for (String path : java.util.List.of("/tr/articles/missing", "/en/articles/missing", "/cv")) {
                assertLanguage(path, preference, "en", 404);
            }
        }
    }

    private void assertLanguage(String path, String preference, String language, int status) throws Exception {
        var response = mvc.perform(get(path).header("Accept-Language", preference)).andReturn().getResponse();
        assertThat(response.getStatus()).as(path).isEqualTo(status);
        assertThat(response.getHeader("Content-Language")).as(path).isEqualTo(language);
        String html = response.getContentAsString();
        assertDocument(html, language, status);
        for (String attribute : java.util.List.of("translate=\"no\"", "<meta name=\"google\"", "<meta name=\"googlebot\"")) {
            assertThatThrownBy(() -> assertDocument(html.replace(attribute, "removed-by-negative-control"), language, status))
                    .as(path + " " + attribute).isInstanceOf(AssertionError.class);
        }
    }

    private static void assertDocument(String html, String language, int status) {
        assertThat(html).contains("lang=\"" + language + "\"", "translate=\"no\"",
                "<meta name=\"google\" content=\"notranslate\"",
                "<meta name=\"googlebot\" content=\"notranslate\"");
        if (status >= 400) assertThat(html).contains("noindex");
    }
}
