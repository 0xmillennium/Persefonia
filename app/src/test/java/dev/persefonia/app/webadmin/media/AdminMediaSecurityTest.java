package dev.persefonia.app.webadmin.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.media.AdminMediaTestConfiguration.AdminMediaGatewayStub;
import dev.persefonia.app.webadmin.media.AdminMediaTestConfiguration.AdminMediaReadModelStub;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(AdminMediaTestConfiguration.class)
@ActiveProfiles({"test", "admin-media-mvc-test"})
class AdminMediaSecurityTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminMediaReadModelStub readModel;
    @Autowired AdminMediaGatewayStub gateway;

    @BeforeEach
    void reset() {
        readModel.reset();
        gateway.reset();
        readModel.add(AdminMediaTestConfiguration.processedImage());
    }

    @Test
    void anonymousGetsAreDeniedOrRedirected() throws Exception {
        assertProtected(mockMvc.perform(get("/admin/media")).andReturn().getResponse().getStatus());
        assertProtected(mockMvc.perform(get("/admin/media/new")).andReturn().getResponse().getStatus());
        assertProtected(mockMvc.perform(get("/admin/media/" + AdminMediaTestConfiguration.IMAGE_ID.value()))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void postsRequireCsrfBeforeCommandHandling() throws Exception {
        mockMvc.perform(multipart("/admin/media").file(jpeg()).with(owner()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(403));
        mockMvc.perform(post("/admin/media/" + AdminMediaTestConfiguration.IMAGE_ID.value())
                        .with(owner())
                        .param("visibility", "PUBLIC")
                        .param("altText", "Alt"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(403));

        assertThat(gateway.uploadMutationCount()).isZero();
        assertThat(gateway.updateMutationCount()).isZero();
    }

    @Test
    void authenticatedOwnerCanUploadAndUpdateMetadataThroughMvcPath() throws Exception {
        mockMvc.perform(multipart("/admin/media").file(jpeg()).with(owner()).with(csrf()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(300, 399));
        mockMvc.perform(post("/admin/media/" + AdminMediaTestConfiguration.IMAGE_ID.value())
                        .with(owner()).with(csrf())
                        .param("visibility", "PUBLIC")
                        .param("altText", "Alt"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(300, 399));

        assertThat(gateway.uploadMutationCount()).isEqualTo(1);
        assertThat(gateway.updateMutationCount()).isEqualTo(1);
    }

    @Test
    void authenticatedNonOwnerCannotUploadOrUpdateAndMutationsDoNotOccur() throws Exception {
        mockMvc.perform(multipart("/admin/media").file(jpeg()).with(editor()).with(csrf()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(403));
        mockMvc.perform(post("/admin/media/" + AdminMediaTestConfiguration.IMAGE_ID.value())
                        .with(editor()).with(csrf())
                        .param("visibility", "PUBLIC")
                        .param("altText", "Alt"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(403));

        assertThat(gateway.uploadMutationCount()).isZero();
        assertThat(gateway.updateMutationCount()).isZero();
    }

    private static void assertProtected(int status) {
        assertThat(status).isBetween(300, 499);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor editor() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "hero.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }
}
