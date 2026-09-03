package dev.persefonia.app.webadmin.settings;

import dev.persefonia.app.audit.MvcAuditTestConfiguration;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.settings.HomepageSettings;
import dev.persefonia.profileportfolio.domain.settings.PositiveInteger;
import dev.persefonia.profileportfolio.domain.settings.SeoDescription;
import dev.persefonia.profileportfolio.domain.settings.SiteName;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsId;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
import dev.persefonia.profileportfolio.domain.settings.TitleSuffix;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("admin-site-settings-mvc-test")
@Import(MvcAuditTestConfiguration.class)
class AdminSiteSettingsTestConfiguration {
    @Bean
    @Primary
    AdminSiteSettingsTestRepository adminSiteSettingsTestRepository() {
        return new AdminSiteSettingsTestRepository();
    }

    static final class AdminSiteSettingsTestRepository implements SitePresentationSettingsRepository {
        private SitePresentationSettings current;

        AdminSiteSettingsTestRepository() {
            reset();
        }

        void reset() {
            current = SitePresentationSettings.create(
                    SitePresentationSettingsId.newId(),
                    SiteName.of("Seeded Site"),
                    ContentLanguage.TR,
                    Set.of(ContentLanguage.TR, ContentLanguage.EN),
                    TitleSuffix.of("| Seed"),
                    SeoDescription.of("Seeded meta description."),
                    null,
                    ThemePreference.SYSTEM,
                    HomepageSettings.of(true, true, false, PositiveInteger.of(3), PositiveInteger.of(5)),
                    Instant.parse("2026-06-16T10:00:00Z"));
        }

        SitePresentationSettings current() {
            return current;
        }

        @Override
        public SitePresentationSettings save(SitePresentationSettings settings) {
            current = settings;
            return settings;
        }

        @Override
        public Optional<SitePresentationSettings> findCurrent() {
            return Optional.ofNullable(current);
        }

        @Override
        public Optional<SitePresentationSettings> findById(SitePresentationSettingsId id) {
            return Optional.ofNullable(current).filter(settings -> settings.id().equals(id));
        }
    }
}
