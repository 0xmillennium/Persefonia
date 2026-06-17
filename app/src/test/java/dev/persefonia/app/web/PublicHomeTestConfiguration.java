package dev.persefonia.app.web;

import dev.persefonia.profileportfolio.application.port.ProjectPublicReadModel;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
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
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("public-home-mvc-test")
class PublicHomeTestConfiguration {
    private final PublicHomeProfileRepository profiles = new PublicHomeProfileRepository();

    @Bean
    @Primary
    SitePresentationSettingsRepository publicHomeSettingsRepository() {
        return new SitePresentationSettingsRepository() {
            private final SitePresentationSettings current = SitePresentationSettings.create(
                    SitePresentationSettingsId.newId(),
                    SiteName.of("Settings Driven Site"),
                    ContentLanguage.EN,
                    Set.of(ContentLanguage.EN),
                    TitleSuffix.of("| Home"),
                    SeoDescription.of("Configured homepage description."),
                    null,
                    ThemePreference.LIGHT,
                    HomepageSettings.of(true, true, true, PositiveInteger.of(3), PositiveInteger.of(5)),
                    Instant.parse("2026-06-16T10:00:00Z"));

            @Override
            public SitePresentationSettings save(SitePresentationSettings settings) {
                return settings;
            }

            @Override
            public Optional<SitePresentationSettings> findCurrent() {
                return Optional.of(current);
            }

            @Override
            public Optional<SitePresentationSettings> findById(SitePresentationSettingsId id) {
                return Optional.of(current).filter(settings -> settings.id().equals(id));
            }
        };
    }

    @Bean
    @Primary
    PublicHomeProfileRepository publicHomeProfileRepository() {
        return profiles;
    }

    @Bean
    @Primary
    ProjectPublicReadModel publicHomeProjectPublicReadModel() {
        return new ProjectPublicReadModel() {
            @Override
            public java.util.List<ProjectSummaryRow> listListedProjects(ContentLanguage language) {
                return java.util.List.of();
            }

            @Override
            public Optional<ProjectDetailRow> findDetail(
                    ProjectId projectId,
                    ContentLanguage language,
                    ProjectSlug expectedSlug) {
                return Optional.empty();
            }

            @Override
            public java.util.List<ProjectSummaryRow> listFeaturedProjects(ContentLanguage language, int limit) {
                return java.util.List.of();
            }
        };
    }

    static final class PublicHomeProfileRepository implements PersonalProfileRepository {
        private PersonalProfile current;

        void setCurrent(PersonalProfile current) {
            this.current = current;
        }

        void reset() {
            current = null;
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
            return Optional.ofNullable(current).filter(PersonalProfile::active);
        }
    }
}
