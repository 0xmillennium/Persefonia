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
        routes.addProjectFound("/tr/projects/unlisted-project", id.value());

        mockMvc.perform(get("/tr/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("unlisted-project"))));
        mockMvc.perform(get("/tr/projects/unlisted-project"))
                .andExpect(status().isOk())
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
}
