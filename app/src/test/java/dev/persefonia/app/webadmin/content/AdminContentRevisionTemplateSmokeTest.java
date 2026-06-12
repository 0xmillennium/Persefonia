package dev.persefonia.app.webadmin.content;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
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
@Import(AdminContentTestConfiguration.class)
@ActiveProfiles({"test", "admin-content-mvc-test"})
class AdminContentRevisionTemplateSmokeTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminContentTestRepository items;
    @Autowired AdminContentTestRevisionRepository revisions;

    @BeforeEach
    void reset() {
        items.reset();
        revisions.reset();
    }

    @Test
    void templateEscapesFieldsAndExposesNoRestoreOrPublicLinks() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        items.add(item);
        revisions.save(AdminContentTestFixtures.revision(item, 1, "Revision <unsafe>"));
        String base = "/admin/content/" + item.id().value();

        mockMvc.perform(get(base + "/revisions")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Revision &lt;unsafe&gt;")))
                .andExpect(content().string(not(containsString("Revision <unsafe>"))))
                .andExpect(content().string(containsString("href=\"" + base + "/edit\"")))
                .andExpect(content().string(not(containsString("restore"))))
                .andExpect(content().string(not(containsString("href=\"/content"))))
                .andExpect(content().string(not(containsString("href=\"/articles"))));
    }
}
