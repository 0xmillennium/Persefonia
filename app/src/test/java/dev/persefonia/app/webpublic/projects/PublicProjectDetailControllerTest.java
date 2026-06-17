package dev.persefonia.app.webpublic.projects;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.InMemoryPublicRouteResolver;
import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import({PublicContentTestConfiguration.class, PublicProjectTestConfiguration.class})
@ActiveProfiles({"test", "public-content-mvc-test", "public-project-mvc-test"})
class PublicProjectDetailControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired InMemoryProjectPublicReadModel projects;
    @Autowired InMemoryPublicRouteResolver routes;

    @BeforeEach
    void reset() {
        projects.reset();
        routes.clear();
    }

    @Test
    void detailRendersPublicProjectOnlyThroughDiscoveryProjection() throws Exception {
        ProjectId projectId = projects.add(ProjectRecord.project("public-detail", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.TR));
        routes.addProjectFound("/tr/projects/public-detail", projectId.value());

        mockMvc.perform(get("/tr/projects/public-detail"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(content().string(containsString("Project public-detail")))
                .andExpect(content().string(containsString("Summary public-detail")))
                .andExpect(content().string(containsString("Java")))
                .andExpect(content().string(containsString("href=\"https://example.test/public-detail\"")))
                .andExpect(content().string(containsString("rel=\"noopener noreferrer\"")))
                .andExpect(content().string(containsString("Problem body")))
                .andExpect(content().string(not(containsString("coverAssetId"))))
                .andExpect(content().string(not(containsString("Media"))))
                .andExpect(content().string(not(containsString("Fake project"))))
                .andExpect(content().string(not(containsString("@vite/client"))))
                .andExpect(content().string(not(containsString("localhost"))));
    }

    @Test
    void detailRendersUnlistedProjectWhenProjectedButDoesNotBypassDiscovery() throws Exception {
        ProjectId projectId = projects.add(ProjectRecord.project("direct-only", Visibility.UNLISTED, Status.ACTIVE, ContentLanguage.EN));

        mockMvc.perform(get("/en/projects/direct-only"))
                .andExpect(status().isNotFound());

        routes.addProjectFound("/en/projects/direct-only", projectId.value());
        mockMvc.perform(get("/en/projects/direct-only"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Project direct-only")));
    }

    @Test
    void detailRejectsPrivateArchivedMissingLocalizationAndSlugMismatch() throws Exception {
        ProjectId privateId = projects.add(ProjectRecord.project("private-detail", Visibility.PRIVATE, Status.ACTIVE, ContentLanguage.TR));
        routes.addProjectFound("/tr/projects/private-detail", privateId.value());
        mockMvc.perform(get("/tr/projects/private-detail")).andExpect(status().isNotFound());

        ProjectId archivedId = projects.add(ProjectRecord.project("archived-detail", Visibility.PUBLIC, Status.ARCHIVED, ContentLanguage.TR));
        routes.addProjectFound("/tr/projects/archived-detail", archivedId.value());
        mockMvc.perform(get("/tr/projects/archived-detail")).andExpect(status().isNotFound());

        ProjectId englishId = projects.add(ProjectRecord.project("english-detail", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.EN));
        routes.addProjectFound("/tr/projects/english-detail", englishId.value());
        mockMvc.perform(get("/tr/projects/english-detail")).andExpect(status().isNotFound());

        ProjectId currentId = projects.add(ProjectRecord.project("current-slug", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.TR));
        routes.addProjectFound("/tr/projects/old-slug", currentId.value());
        mockMvc.perform(get("/tr/projects/old-slug")).andExpect(status().isNotFound());
    }

    @Test
    void detailRejectsWrongDiscoveryMetadata() throws Exception {
        var projectId = java.util.UUID.randomUUID();
        routes.addFound(found(projectId, SourceContext.CONTENT_PUBLISHING, SourceType.PROJECT,
                DiscoverableResourceType.PROJECT, DiscoveryLanguage.TR, "/tr/projects/wrong-context"));
        mockMvc.perform(get("/tr/projects/wrong-context")).andExpect(status().isNotFound());

        routes.addFound(found(projectId, SourceContext.PROFILE_PORTFOLIO, SourceType.CONTENT_ITEM,
                DiscoverableResourceType.PROJECT, DiscoveryLanguage.TR, "/tr/projects/wrong-source-type"));
        mockMvc.perform(get("/tr/projects/wrong-source-type")).andExpect(status().isNotFound());

        routes.addFound(found(projectId, SourceContext.PROFILE_PORTFOLIO, SourceType.PROJECT,
                DiscoverableResourceType.TAG, DiscoveryLanguage.TR, "/tr/projects/wrong-resource-type"));
        mockMvc.perform(get("/tr/projects/wrong-resource-type")).andExpect(status().isNotFound());

        routes.addFound(found(projectId, SourceContext.PROFILE_PORTFOLIO, SourceType.PROJECT,
                DiscoverableResourceType.PROJECT, DiscoveryLanguage.EN, "/tr/projects/wrong-language"));
        mockMvc.perform(get("/tr/projects/wrong-language")).andExpect(status().isNotFound());
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
}
