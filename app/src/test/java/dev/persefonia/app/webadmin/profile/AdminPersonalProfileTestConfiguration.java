package dev.persefonia.app.webadmin.profile;

import dev.persefonia.app.audit.MvcAuditTestConfiguration;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfile;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfileRepository;
import dev.persefonia.profileportfolio.domain.profile.ProfileId;
import dev.persefonia.profileportfolio.domain.settings.HomepageSettings;
import dev.persefonia.profileportfolio.domain.settings.PositiveInteger;
import dev.persefonia.profileportfolio.domain.settings.SiteName;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsId;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("admin-personal-profile-mvc-test")
@Import(MvcAuditTestConfiguration.class)
class AdminPersonalProfileTestConfiguration {
    @Bean
    @Primary
    AdminPersonalProfileTestRepository adminPersonalProfileTestRepository() {
        return new AdminPersonalProfileTestRepository();
    }

    @Bean
    @Primary
    AdminPersonalProfileSettingsRepository adminPersonalProfileSettingsRepository() {
        return new AdminPersonalProfileSettingsRepository();
    }

    static final class AdminPersonalProfileTestRepository implements PersonalProfileRepository {
        private PersonalProfile current;

        void reset() {
            current = null;
        }

        PersonalProfile current() {
            return current;
        }

        @Override
        public PersonalProfile save(PersonalProfile profile) {
            current = profile;
            return profile;
        }

        @Override
        public Optional<PersonalProfile> findById(ProfileId id) {
            return Optional.ofNullable(current).filter(profile -> profile.id().equals(id));
        }

        @Override
        public Optional<PersonalProfile> findActiveProfile() {
            return Optional.ofNullable(current).filter(profile -> profile.active());
        }
    }

    static final class AdminPersonalProfileSettingsRepository implements SitePresentationSettingsRepository {
        private SitePresentationSettings current;

        AdminPersonalProfileSettingsRepository() {
            reset();
        }

        void reset() {
            current = SitePresentationSettings.create(
                    SitePresentationSettingsId.newId(),
                    SiteName.of("Seeded Site"),
                    ContentLanguage.TR,
                    Set.of(ContentLanguage.TR, ContentLanguage.EN),
                    null,
                    null,
                    null,
                    ThemePreference.SYSTEM,
                    HomepageSettings.of(true, true, false, PositiveInteger.of(3), PositiveInteger.of(5)),
                    Instant.parse("2026-06-16T10:00:00Z"));
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
