package dev.persefonia.app.webadmin.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.cv.AdminCvTestConfiguration.AdminCvEligibilityStub;
import dev.persefonia.app.webadmin.cv.AdminCvTestConfiguration.AdminCvProfileRepositoryStub;
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
@Import(AdminCvTestConfiguration.class)
@ActiveProfiles({"test", "admin-cv-mvc-test"})
class AdminCvSecurityTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminCvProfileRepositoryStub profiles;
    @Autowired AdminCvEligibilityStub eligibility;

    @BeforeEach
    void reset() {
        profiles.reset();
        eligibility.reset();
    }

    @Test
    void anonymousGetIsDeniedOrRedirected() throws Exception {
        int status = mockMvc.perform(get("/admin/cv")).andReturn().getResponse().getStatus();

        assertThat(status).isBetween(300, 499);
    }

    @Test
    void postRequiresCsrfBeforeMutation() throws Exception {
        mockMvc.perform(post("/admin/cv").with(owner())
                        .param("enAssetId", AdminCvTestConfiguration.PUBLIC_PDF_ID.value().toString()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(403));

        assertThat(profiles.saveCount()).isZero();
    }

    @Test
    void ownerCanPost() throws Exception {
        mockMvc.perform(post("/admin/cv").with(owner()).with(csrf())
                        .param("enAssetId", AdminCvTestConfiguration.PUBLIC_PDF_ID.value().toString()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(300, 399));

        assertThat(profiles.saveCount()).isEqualTo(1);
    }

    @Test
    void nonOwnerCannotPostAndMutationDoesNotOccur() throws Exception {
        mockMvc.perform(post("/admin/cv").with(editor()).with(csrf())
                        .param("enAssetId", AdminCvTestConfiguration.PUBLIC_PDF_ID.value().toString()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(403));

        assertThat(profiles.saveCount()).isZero();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor editor() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));
    }
}
