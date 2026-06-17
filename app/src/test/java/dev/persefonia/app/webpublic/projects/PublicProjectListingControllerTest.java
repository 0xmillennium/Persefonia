package dev.persefonia.app.webpublic.projects;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration.InMemoryProjectPublicReadModel;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration.ProjectRecord;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration.Status;
import dev.persefonia.app.webpublic.projects.PublicProjectTestConfiguration.Visibility;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
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
class PublicProjectListingControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired InMemoryProjectPublicReadModel projects;

    @BeforeEach
    void reset() {
        projects.reset();
    }

    @Test
    void listingRendersOnlyPublicNonArchivedLocalizedProjects() throws Exception {
        projects.add(ProjectRecord.project("listed", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.TR));
        projects.add(ProjectRecord.project("unlisted", Visibility.UNLISTED, Status.ACTIVE, ContentLanguage.TR));
        projects.add(ProjectRecord.project("private", Visibility.PRIVATE, Status.ACTIVE, ContentLanguage.TR));
        projects.add(ProjectRecord.project("archived", Visibility.PUBLIC, Status.ARCHIVED, ContentLanguage.TR));
        projects.add(ProjectRecord.project("english", Visibility.PUBLIC, Status.ACTIVE, ContentLanguage.EN));

        mockMvc.perform(get("/tr/projects"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(containsString("/tr/projects/listed")))
                .andExpect(content().string(containsString("Project listed")))
                .andExpect(content().string(not(containsString("/tr/projects/unlisted"))))
                .andExpect(content().string(not(containsString("/tr/projects/private"))))
                .andExpect(content().string(not(containsString("/tr/projects/archived"))))
                .andExpect(content().string(not(containsString("/en/projects/english"))));
    }

    @Test
    void listingEmptyStateAndTemplateStayPublicSafe() throws Exception {
        mockMvc.perform(get("/en/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No public projects are currently available.")))
                .andExpect(content().string(not(containsString("coverAssetId"))))
                .andExpect(content().string(not(containsString("Media"))))
                .andExpect(content().string(not(containsString("Fake project"))))
                .andExpect(content().string(not(containsString("@vite/client"))))
                .andExpect(content().string(not(containsString("localhost"))));
    }
}
