package dev.persefonia.app.exposure;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.InMemoryPublicRouteResolver;
import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration.InMemoryProjectPublicReadModel;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration.ProjectRecord;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration.Status;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration.Visibility;
import dev.persefonia.discovery.application.contract.CanonicalUrl;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.route.PublicRouteResolution;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
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
        ProjectPublicExposureTest.ProjectPublicExposureSettingsConfiguration.class
})
@ActiveProfiles({"test", "public-content-mvc-test", "public-project-mvc-test", "project-public-exposure-test"})
class ProjectPublicExposureTest {
    @Autowired MockMvc mockMvc;
    @Autowired InMemoryProjectPublicReadModel projects;
    @Autowired InMemoryPublicRouteResolver routes;

    @BeforeEach
    void reset() {
        projects.reset();
        routes.clear();
    }

    @Test
    void publicProjectAppearsInListingAndRendersDetail() throws Exception {
        ProjectId id = projects.add(ProjectRecord.project("public-project", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.TR));
        routes.addProjectFound("/tr/projects/public-project", id.value());

        mockMvc.perform(get("/tr/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/tr/projects/public-project")));
        mockMvc.perform(get("/tr/projects/public-project"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Project public-project")));
    }

    @Test
    void unlistedProjectIsNotListedButRendersThroughDirectProjection() throws Exception {
        ProjectId id = projects.add(ProjectRecord.project("unlisted-project", Visibility.UNLISTED, Status.ACTIVE, ContentLanguage.TR));
        routes.addUnlistedProjectFound("/tr/projects/unlisted-project", id.value());

        mockMvc.perform(get("/tr/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("unlisted-project"))));
        mockMvc.perform(get("/tr/projects/unlisted-project"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex, follow\">")))
                .andExpect(content().string(containsString("Project unlisted-project")));
    }

    @Test
    void privateArchivedAndMissingLocalizationAreNotListedOrRendered() throws Exception {
        ProjectId privateId = projects.add(ProjectRecord.project("private-project", Visibility.PRIVATE, Status.ACTIVE, ContentLanguage.TR));
        ProjectId archivedId = projects.add(ProjectRecord.project("archived-project", Visibility.PUBLIC, Status.ARCHIVED, ContentLanguage.TR));
        ProjectId englishId = projects.add(ProjectRecord.project("english-project", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.EN));
        routes.addProjectFound("/tr/projects/private-project", privateId.value());
        routes.addProjectFound("/tr/projects/archived-project", archivedId.value());
        routes.addProjectFound("/tr/projects/english-project", englishId.value());

        mockMvc.perform(get("/tr/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("private-project"))))
                .andExpect(content().string(not(containsString("archived-project"))))
                .andExpect(content().string(not(containsString("english-project"))));
        mockMvc.perform(get("/tr/projects/private-project")).andExpect(status().isNotFound());
        mockMvc.perform(get("/tr/projects/archived-project")).andExpect(status().isNotFound());
        mockMvc.perform(get("/tr/projects/english-project")).andExpect(status().isNotFound());
    }

    @Test
    void removedProjectionNoLongerResolvesAndWrongResourceTypeReturnsNotFound() throws Exception {
        ProjectId id = projects.add(ProjectRecord.project("projected", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.EN));

        mockMvc.perform(get("/en/projects/projected")).andExpect(status().isNotFound());

        routes.addProjectFound("/en/projects/projected", id.value());
        mockMvc.perform(get("/en/projects/projected")).andExpect(status().isOk());

        routes.clear();
        mockMvc.perform(get("/en/projects/projected")).andExpect(status().isNotFound());
    }

    @Test
    void wrongDiscoveryMetadataReturnsNotFound() throws Exception {
        var projectId = java.util.UUID.randomUUID();
        routes.addFound(found(projectId, SourceContext.CONTENT_PUBLISHING, SourceType.PROJECT,
                DiscoverableResourceType.PROJECT, DiscoveryLanguage.TR, "/tr/projects/wrong-context"));
        mockMvc.perform(get("/tr/projects/wrong-context")).andExpect(status().isNotFound());

        routes.addFound(found(projectId, SourceContext.PROFILE_PORTFOLIO, SourceType.CONTENT_ITEM,
                DiscoverableResourceType.PROJECT, DiscoveryLanguage.TR, "/tr/projects/wrong-source"));
        mockMvc.perform(get("/tr/projects/wrong-source")).andExpect(status().isNotFound());

        routes.addFound(found(projectId, SourceContext.PROFILE_PORTFOLIO, SourceType.PROJECT,
                DiscoverableResourceType.TAG, DiscoveryLanguage.TR, "/tr/projects/wrong-resource"));
        mockMvc.perform(get("/tr/projects/wrong-resource")).andExpect(status().isNotFound());
    }

    @Test
    void homepageFeaturedIncludesPublicFeaturedAndExcludesUnlistedPrivateAndArchived() throws Exception {
        projects.add(ProjectRecord.project("public-featured", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.EN).featured(true));
        projects.add(ProjectRecord.project("unlisted-featured", Visibility.UNLISTED, Status.ACTIVE, ContentLanguage.EN).featured(true));
        projects.add(ProjectRecord.project("private-featured", Visibility.PRIVATE, Status.ACTIVE, ContentLanguage.EN).featured(true));
        projects.add(ProjectRecord.project("archived-featured", Visibility.PUBLIC, Status.ARCHIVED, ContentLanguage.EN).featured(true));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Featured projects")))
                .andExpect(content().string(containsString("/en/projects/public-featured")))
                .andExpect(content().string(not(containsString("unlisted-featured"))))
                .andExpect(content().string(not(containsString("private-featured"))))
                .andExpect(content().string(not(containsString("archived-featured"))));
    }

    private static PublicRouteResolution.Found found(
            java.util.UUID projectId,
            SourceContext sourceContext,
            SourceType sourceType,
            DiscoverableResourceType resourceType,
            DiscoveryLanguage language,
            String publicUrl) {
        return new PublicRouteResolution.Found(
                sourceContext,
                sourceType,
                new SourceEntityId(projectId),
                resourceType,
                RoutePurpose.DETAIL,
                language,
                new PublicUrl(publicUrl),
                new CanonicalUrl("https://0xmillennium.dev" + publicUrl),
                IndexingPolicy.NO_INDEX);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("project-public-exposure-test")
    static class ProjectPublicExposureSettingsConfiguration {
        @Bean
        @Primary
        SitePresentationSettingsRepository projectPublicExposureSettingsRepository() {
            return new SitePresentationSettingsRepository() {
                private final SitePresentationSettings current = SitePresentationSettings.create(
                        SitePresentationSettingsId.newId(),
                        SiteName.of("Exposure"),
                        ContentLanguage.EN,
                        Set.of(ContentLanguage.EN, ContentLanguage.TR),
                        null,
                        null,
                        null,
                        ThemePreference.SYSTEM,
                        HomepageSettings.of(true, true, false, PositiveInteger.of(3), PositiveInteger.of(5)),
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
        PersonalProfileRepository projectPublicExposureProfileRepository() {
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
}
