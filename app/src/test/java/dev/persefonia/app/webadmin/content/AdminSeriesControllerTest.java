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
import dev.persefonia.contentpublishing.application.service.SeriesCommandGateway;
import dev.persefonia.contentpublishing.domain.model.series.SeriesValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
    @MockitoSpyBean SeriesCommandGateway commands;

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

    @ParameterizedTest
    @CsvSource(value = {"INVALID|Valid title|valid-slug|language", "EN| |valid-slug|title",
            "EN|Valid title|bad slug|slug", "EN|Valid title| |slug"},
            delimiter = '|', ignoreLeadingAndTrailingWhitespace = false)
    void creationErrorsLinkToTheActualInvalidControl(String language, String title, String slug, String field)
            throws Exception {
        var result = mockMvc.perform(post("/admin/series")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf()).param("language", language).param("title", title).param("slug", slug))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("noindex,nofollow,noarchive")))
                .andReturn();
        var document = AdminFormAssertions.accessibleDocument(result.getResponse().getContentAsString());
        AdminFormAssertions.fieldError(document, field);
        assertThat(document.text()).doesNotContain("Exception", "INVALID");
        assertThat(series.all()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "description"})
    void lengthValidationKeepsItsFieldSpecificDestination(String field) throws Exception {
        var result = mockMvc.perform(post("/admin/series")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf()).param("language", "EN")
                        .param("title", field.equals("title") ? "x".repeat(121) : "Valid title")
                        .param("slug", "valid-slug")
                        .param("description", field.equals("description") ? "x".repeat(501) : "Valid description"))
                .andExpect(status().isOk()).andReturn();
        AdminFormAssertions.fieldError(
                AdminFormAssertions.accessibleDocument(result.getResponse().getContentAsString()), field);
        assertThat(series.all()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unassignableValidationUsesAnHonestFocusableGlobalError(boolean illegalArgument) throws Exception {
        RuntimeException failure = illegalArgument
                ? new IllegalArgumentException("Synthetic internal detail must stay private")
                : new SeriesValidationException("Synthetic internal detail must stay private");
        org.mockito.Mockito.doThrow(failure).when(commands).create(org.mockito.ArgumentMatchers.any());
        var result = mockMvc.perform(post("/admin/series")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf()).param("language", "EN").param("title", "Valid title").param("slug", "valid-slug"))
                .andExpect(status().isOk()).andReturn();
        var document = AdminFormAssertions.accessibleDocument(result.getResponse().getContentAsString());
        assertThat(document.select(".field-errors")).isEmpty();
        var form = document.getElementById("series");
        assertThat(form).isNotNull();
        assertThat(form.tagName()).isEqualTo("form");
        assertThat(form.attr("tabindex")).isEqualTo("-1");
        assertThat(form.attr("aria-describedby")).isEqualTo("series-errors");
        assertThat(document.select("#series-errors a[href='#series']")).hasSize(1);
        assertThat(document.select("#language[aria-invalid]")).isEmpty();
        assertThat(document.text()).doesNotContain("Synthetic internal detail", "Exception");
        assertThat(series.all()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void editValidationRetainsFieldAndGlobalDestinations(boolean global) throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
        mockMvc.perform(post("/admin/series").with(owner).with(csrf())
                        .param("language", "EN").param("title", "Original title").param("slug", "original-slug"))
                .andExpect(status().is3xxRedirection());
        var created = series.all().getFirst();
        if (global) {
            org.mockito.Mockito.doThrow(new SeriesValidationException("Synthetic private detail"))
                    .when(commands).update(org.mockito.ArgumentMatchers.any());
        }
        var result = mockMvc.perform(post("/admin/series/" + created.id().value()).with(owner).with(csrf())
                        .param("title", "Updated title").param("slug", global ? "updated-slug" : "bad slug"))
                .andExpect(status().isOk()).andReturn();
        var document = AdminFormAssertions.accessibleDocument(result.getResponse().getContentAsString());
        if (global) {
            assertThat(document.select("#series-errors a[href='#series']")).hasSize(1);
            assertThat(document.getElementById("series").attr("tabindex")).isEqualTo("-1");
            assertThat(document.getElementById("series").attr("aria-describedby")).isEqualTo("series-errors");
            assertThat(document.text()).doesNotContain("Synthetic private detail");
        } else {
            AdminFormAssertions.fieldError(document, "slug");
        }
        assertThat(series.findById(created.id()).orElseThrow().title().value()).isEqualTo("Original title");
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
