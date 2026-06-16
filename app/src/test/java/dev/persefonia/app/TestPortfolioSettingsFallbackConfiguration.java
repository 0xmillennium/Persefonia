package dev.persefonia.app;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfile;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfileRepository;
import dev.persefonia.profileportfolio.domain.profile.ProfileId;
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
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestPortfolioSettingsFallbackConfiguration {
    @Bean
    @Primary
    SitePresentationSettingsRepository testPortfolioSettingsRepository() {
        SitePresentationSettings settings = SitePresentationSettings.create(
                SitePresentationSettingsId.newId(),
                SiteName.of("Persefonia"),
                ContentLanguage.EN,
                Set.of(ContentLanguage.EN, ContentLanguage.TR),
                TitleSuffix.of("| Home"),
                SeoDescription.of("Persefonia homepage."),
                null,
                ThemePreference.SYSTEM,
                HomepageSettings.of(false, false, false, PositiveInteger.of(3), PositiveInteger.of(5)),
                Instant.parse("2026-06-16T10:00:00Z"));
        return new SitePresentationSettingsRepository() {
            @Override
            public SitePresentationSettings save(SitePresentationSettings settings) {
                return settings;
            }

            @Override
            public Optional<SitePresentationSettings> findCurrent() {
                return Optional.of(settings);
            }

            @Override
            public Optional<SitePresentationSettings> findById(SitePresentationSettingsId id) {
                return Optional.of(settings).filter(current -> current.id().equals(id));
            }
        };
    }

    @Bean
    @Primary
    PersonalProfileRepository testPersonalProfileRepository() {
        return new PersonalProfileRepository() {
            @Override
            public PersonalProfile save(PersonalProfile profile) {
                return profile;
            }

            @Override
            public Optional<PersonalProfile> findById(ProfileId id) {
                return Optional.empty();
            }

            @Override
            public Optional<PersonalProfile> findActiveProfile() {
                return Optional.empty();
            }
        };
    }
}
