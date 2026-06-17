package dev.persefonia.app.webpublic.projects;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.InMemoryPublicRouteResolver;
import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
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
class PublicProjectSecurityAndCacheTest {
    @Autowired MockMvc mockMvc;
    @Autowired InMemoryProjectPublicReadModel projects;
    @Autowired InMemoryPublicRouteResolver routes;

    @BeforeEach
    void reset() {
        projects.reset();
        routes.clear();
    }

    @Test
    void projectListingAndEligibleDetailAreAnonymousAndUsePublicCacheNotAdminNoStore() throws Exception {
        ProjectId projectId = projects.add(ProjectRecord.project("secure-project", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.TR));
        routes.addProjectFound("/tr/projects/secure-project", projectId.value());

        mockMvc.perform(get("/tr/projects"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", not(containsString("no-store"))))
                .andExpect(header().string("Cache-Control", not(containsString("private"))));

        mockMvc.perform(get("/tr/projects/secure-project"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", not(containsString("no-store"))))
                .andExpect(header().string("Cache-Control", not(containsString("private"))));
    }

    @Test
    void missingProjectDetailUsesPrivateNoStoreNotFoundCache() throws Exception {
        mockMvc.perform(get("/tr/projects/missing-project"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")));
    }
}
