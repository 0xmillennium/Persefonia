package dev.persefonia.app.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration.InMemoryProjectPublicReadModel;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration.ProjectRecord;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration.Status;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration.Visibility;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import({
        PublicContentTestConfiguration.class,
        PublicProjectTestConfiguration.class,
        PublicHomeFeaturedProjectsTest.HomeFeaturedSettingsConfiguration.class
})
@ActiveProfiles({"test", "public-content-mvc-test", "public-project-mvc-test", "public-home-featured-projects-test"})
class PublicHomeFeaturedProjectsTest {
    @Autowired MockMvc mockMvc;
    @Autowired InMemoryProjectPublicReadModel projects;
    @Autowired MutableSettingsRepository settings;

    @BeforeEach
    void reset() {
        projects.reset();
        settings.configure(ContentLanguage.EN, true, 3);
    }

    @Test
    void showFeaturedProjectsFalseRendersNoFeaturedSection() throws Exception {
        settings.configure(ContentLanguage.EN, false, 3);
        projects.add(ProjectRecord.project("featured", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.EN).featured(true));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Featured projects"))))
                .andExpect(content().string(not(containsString("featured"))));
    }

    @Test
    void homepageFeaturedUsesSettingsDefaultLanguageAndExcludesNonPublicStates() throws Exception {
        projects.add(ProjectRecord.project("english-featured", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.EN).featured(true));
        projects.add(ProjectRecord.project("turkish-featured", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.TR).featured(true));
        projects.add(ProjectRecord.project("unlisted-featured", Visibility.UNLISTED, Status.ACTIVE, ContentLanguage.EN).featured(true));
        projects.add(ProjectRecord.project("private-featured", Visibility.PRIVATE, Status.ACTIVE, ContentLanguage.EN).featured(true));
        projects.add(ProjectRecord.project("archived-featured", Visibility.PUBLIC, Status.ARCHIVED, ContentLanguage.EN).featured(true));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Featured projects")))
                .andExpect(content().string(containsString("/en/projects/english-featured")))
                .andExpect(content().string(not(containsString("turkish-featured"))))
                .andExpect(content().string(not(containsString("unlisted-featured"))))
                .andExpect(content().string(not(containsString("private-featured"))))
                .andExpect(content().string(not(containsString("archived-featured"))))
                .andExpect(content().string(not(containsString("Fake project"))));
    }

    @Test
    void homepageFeaturedRespectsLimit() throws Exception {
        settings.configure(ContentLanguage.EN, true, 1);
        projects.add(ProjectRecord.project("first-featured", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.EN).featured(true));
        projects.add(ProjectRecord.project("second-featured", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.EN).featured(true));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Featured projects")))
                .andExpect(content().string(containsString("first-featured")))
                .andExpect(content().string(not(containsString("second-featured"))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("public-home-featured-projects-test")
    static class HomeFeaturedSettingsConfiguration {
        @Bean
        @Primary
        MutableSettingsRepository mutableHomepageSettingsRepository() {
            return new MutableSettingsRepository();
        }

        @Bean
        @Primary
        PersonalProfileRepository emptyPersonalProfileRepository() {
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

    static final class MutableSettingsRepository implements SitePresentationSettingsRepository {
        private SitePresentationSettings current = settings(ContentLanguage.EN, true, 3);

        void configure(ContentLanguage defaultLanguage, boolean showFeaturedProjects, int featuredLimit) {
            current = settings(defaultLanguage, showFeaturedProjects, featuredLimit);
        }

        @Override
        public SitePresentationSettings save(SitePresentationSettings settings) {
            current = settings;
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

        private static SitePresentationSettings settings(
                ContentLanguage defaultLanguage,
                boolean showFeaturedProjects,
                int featuredLimit) {
            return SitePresentationSettings.create(
                    SitePresentationSettingsId.newId(),
                    SiteName.of("Home"),
                    defaultLanguage,
                    Set.of(ContentLanguage.TR, ContentLanguage.EN),
                    null,
                    null,
                    null,
                    ThemePreference.SYSTEM,
                    HomepageSettings.of(showFeaturedProjects, true, false, PositiveInteger.of(featuredLimit), PositiveInteger.of(5)),
                    Instant.parse("2026-06-16T10:00:00Z"));
        }
    }
}
