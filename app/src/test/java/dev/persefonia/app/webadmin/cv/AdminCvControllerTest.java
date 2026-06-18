package dev.persefonia.app.webadmin.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.cv.AdminCvTestConfiguration.AdminCvEligibilityStub;
import dev.persefonia.app.webadmin.cv.AdminCvTestConfiguration.AdminCvProfileRepositoryStub;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
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
@Import(AdminCvTestConfiguration.class)
@ActiveProfiles({"test", "admin-cv-mvc-test"})
class AdminCvControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminCvProfileRepositoryStub profiles;
    @Autowired AdminCvEligibilityStub eligibility;

    @BeforeEach
    void reset() {
        profiles.reset();
        eligibility.reset();
    }

    @Test
    void getRendersSupportedLanguagesAndCandidates() throws Exception {
        mockMvc.perform(get("/admin/cv").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CV")))
                .andExpect(content().string(containsString("TR")))
                .andExpect(content().string(containsString("EN")))
                .andExpect(content().string(containsString("cv-en.pdf")))
                .andExpect(content().string(containsString("href=\"/admin/media\"")))
                .andExpect(content().string(not(containsString("storage_path"))))
                .andExpect(content().string(not(containsString("/media/assets/" + AdminCvTestConfiguration.PUBLIC_PDF_ID.value()))))
                .andExpect(content().string(not(containsString("href=\"/cv\""))))
                .andExpect(content().string(not(containsString("href=\"/resume\""))));
    }

    @Test
    void getShowsCurrentSelections() throws Exception {
        profiles.profile().selectDocument(
                ContentLanguage.EN,
                AdminCvTestConfiguration.PUBLIC_PDF_ID,
                null,
                AdminCvTestConfiguration.NOW);

        mockMvc.perform(get("/admin/cv").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("selected>cv-en.pdf")))
                .andExpect(content().string(containsString(AdminCvTestConfiguration.PUBLIC_PDF_ID.value().toString())));
    }

    @Test
    void getShowsEmptyStateWhenNoPublicPdfsExist() throws Exception {
        eligibility.clear();

        mockMvc.perform(get("/admin/cv").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No public PDF assets are available.")))
                .andExpect(content().string(containsString("href=\"/admin/media\"")));
    }

    @Test
    void postUpdatesSelections() throws Exception {
        mockMvc.perform(post("/admin/cv").with(owner()).with(csrf())
                        .param("enAssetId", AdminCvTestConfiguration.PUBLIC_PDF_ID.value().toString())
                        .param("enDisplayLabel", "English CV"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/cv?saved"));

        assertThat(profiles.profile().documentFor(ContentLanguage.EN)).isPresent();
        assertThat(profiles.saveCount()).isEqualTo(1);
    }

    @Test
    void postClearsSelection() throws Exception {
        profiles.profile().selectDocument(
                ContentLanguage.EN,
                AdminCvTestConfiguration.PUBLIC_PDF_ID,
                null,
                AdminCvTestConfiguration.NOW);

        mockMvc.perform(post("/admin/cv").with(owner()).with(csrf())
                        .param("enAssetId", ""))
                .andExpect(status().is3xxRedirection());

        assertThat(profiles.profile().documentFor(ContentLanguage.EN)).isEmpty();
    }

    @Test
    void validationErrorRedisplaysPage() throws Exception {
        mockMvc.perform(post("/admin/cv").with(owner()).with(csrf())
                        .param("enAssetId", "not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Must be a valid UUID.")));

        assertThat(profiles.saveCount()).isZero();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }
}
