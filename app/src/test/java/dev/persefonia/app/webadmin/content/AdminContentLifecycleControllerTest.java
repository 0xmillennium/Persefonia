package dev.persefonia.app.webadmin.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
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
class AdminContentLifecycleControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminContentTestRepository items;
    @Autowired AdminContentTestRevisionRepository revisions;

    @BeforeEach
    void reset() {
        items.reset();
        revisions.reset();
    }

    @Test
    void publishUsesApplicationBehaviorAndRedirectsWithFeedback() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        items.add(item);
        String path = "/admin/content/" + item.id().value();

        mockMvc.perform(post(path + "/publish").with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(path + "/edit?published=true"));

        var published = items.findById(item.id()).orElseThrow();
        assertThat(published.status()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(published.renderSnapshot().orElseThrow().renderedHtml().value())
                .contains("<h1").doesNotContain("<script", "onerror");
        assertThat(revisions.findByContentId(item.id())).hasSize(1);
    }

    @Test
    void incompletePublishRedirectsWithSafeFailureAndDoesNotMutate() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        item.clearMarkdownSource(AdminContentTestFixtures.CREATED.plusSeconds(10));
        items.add(item);
        String path = "/admin/content/" + item.id().value();

        mockMvc.perform(post(path + "/publish").with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(path + "/edit?publishFailed=true"));

        assertThat(items.findById(item.id()).orElseThrow().status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(revisions.findByContentId(item.id())).isEmpty();
    }

    @Test
    void unpublishUsesApplicationBehaviorAndPreservesPublishedAt() throws Exception {
        var item = AdminContentTestFixtures.published();
        var publishedAt = item.publishedAt();
        items.add(item);
        String path = "/admin/content/" + item.id().value();

        mockMvc.perform(post(path + "/unpublish").with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(path + "/edit?unpublished=true"));

        var unpublished = items.findById(item.id()).orElseThrow();
        assertThat(unpublished.status()).isEqualTo(ContentStatus.UNPUBLISHED);
        assertThat(unpublished.publishedAt()).isEqualTo(publishedAt);
        assertThat(unpublished.unpublishedAt()).isPresent();
        assertThat(revisions.findByContentId(item.id())).isEmpty();
    }

    @Test
    void invalidUnpublishRedirectsWithSafeFailure() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        items.add(item);
        String path = "/admin/content/" + item.id().value();

        mockMvc.perform(post(path + "/unpublish").with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(path + "/edit?unpublishFailed=true"));

        assertThat(items.findById(item.id()).orElseThrow().status()).isEqualTo(ContentStatus.DRAFT);
    }

    @Test
    void archiveUsesApplicationBehaviorAndRedirectsToList() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        items.add(item);

        mockMvc.perform(post("/admin/content/" + item.id().value() + "/archive").with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/content?archived=true"));

        assertThat(items.findById(item.id()).orElseThrow().status()).isEqualTo(ContentStatus.ARCHIVED);
        assertThat(revisions.findByContentId(item.id())).isEmpty();
    }

    @Test
    void missingArchiveTargetReturnsNotFound() throws Exception {
        mockMvc.perform(post("/admin/content/" + java.util.UUID.randomUUID() + "/archive").with(owner()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }
}
