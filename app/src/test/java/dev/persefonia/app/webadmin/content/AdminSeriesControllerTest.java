package dev.persefonia.app.webadmin.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
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
@Import({AdminContentTestConfiguration.class, AdminSeriesTestConfiguration.class})
@ActiveProfiles({"test", "admin-content-mvc-test", "admin-series-mvc-test"})
class AdminSeriesControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminContentTestRepository contentItems;
    @Autowired AdminSeriesTestRepository series;

    @BeforeEach
    void reset() {
        contentItems.reset();
        series.reset();
    }

    @Test
    void ownerCanViewSeriesListAndNewForm() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(get("/admin/series").with(owner))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().string(containsString("noindex,nofollow,noarchive")));
        mockMvc.perform(get("/admin/series/new").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create series")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void ownerCanCreateUpdateArchiveAndManageEntriesWithCsrf() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
        var first = AdminContentTestFixtures.completeDraft();
        var second = completeDraft("second-series-entry");
        contentItems.add(first);
        contentItems.add(second);

        mockMvc.perform(post("/admin/series").with(owner).with(csrf())
                        .param("language", "EN")
                        .param("title", "Learning Path")
                        .param("slug", "learning-path")
                        .param("description", "Editorial sequence"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/series/*/edit?created"));

        var created = series.all().getFirst();
        String seriesId = created.id().value().toString();
        mockMvc.perform(get("/admin/series/" + seriesId + "/edit").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit series")));
        mockMvc.perform(post("/admin/series/" + seriesId).with(owner).with(csrf())
                        .param("title", "Updated Path")
                        .param("slug", "updated-path")
                        .param("description", "Updated"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/series/" + seriesId + "/entries").with(owner).with(csrf())
                        .param("contentItemId", first.id().value().toString()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/series/" + seriesId + "/entries").with(owner).with(csrf())
                        .param("contentItemId", second.id().value().toString()))
                .andExpect(status().is3xxRedirection());

        var entries = series.findById(created.id()).orElseThrow().entries();
        mockMvc.perform(post("/admin/series/" + seriesId + "/entries/reorder").with(owner).with(csrf())
                        .param("orderedEntryIds", entries.get(1).id().value().toString(), entries.get(0).id().value().toString()))
                .andExpect(status().is3xxRedirection());
        var reordered = series.findById(created.id()).orElseThrow().entries();
        mockMvc.perform(post("/admin/series/" + seriesId + "/entries/" + reordered.getFirst().id().value() + "/remove")
                        .with(owner).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/series/" + seriesId + "/archive").with(owner).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(series.findById(created.id()).orElseThrow().isArchived()).isTrue();
    }

    @Test
    void anonymousCannotAccessSeriesAdminAndNonOwnerCannotMutateSeries() throws Exception {
        var editor = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));
        mockMvc.perform(get("/admin/series")).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/admin/series").with(editor).with(csrf())
                        .param("language", "EN")
                        .param("title", "Learning Path")
                        .param("slug", "learning-path"))
                .andExpect(status().isForbidden());
        assertThat(series.all()).isEmpty();
    }

    @Test
    void postWithoutCsrfRejectedAndValidationErrorsRender() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
        mockMvc.perform(post("/admin/series").with(owner)
                        .param("language", "EN")
                        .param("title", "Learning Path")
                        .param("slug", "learning-path"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/series").with(owner).with(csrf())
                        .param("language", "EN")
                        .param("title", " ")
                        .param("slug", "bad slug"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This field is required.")));
    }

    private static ContentItem completeDraft(String slug) {
        ContentItem item = ContentItem.createDraft(
                ContentId.newId(), ContentType.ARTICLE, ContentVisibility.PRIVATE, ContentLanguage.EN,
                AdminContentTestFixtures.CREATED);
        item.changeSlug(Slug.of(slug), AdminContentTestFixtures.CREATED.plusSeconds(1));
        item.changeTitle(Title.of("Title " + slug), AdminContentTestFixtures.CREATED.plusSeconds(1));
        item.changeSummary(Summary.of("Summary " + slug), AdminContentTestFixtures.CREATED.plusSeconds(1));
        item.changeMarkdownSource(MarkdownSource.of("# " + slug), AdminContentTestFixtures.CREATED.plusSeconds(1));
        item.changeMetadata(
                ContentMetadata.withCanonicalPath(CanonicalPath.of("/articles/" + slug)),
                AdminContentTestFixtures.CREATED.plusSeconds(1));
        return item;
    }
}
