package dev.persefonia.app.webadmin.content;

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
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import java.time.Instant;
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
class AdminTranslationGroupControllerTest {
    private static final Instant CREATED = Instant.parse("2026-06-12T08:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired AdminContentTestRepository items;
    @Autowired AdminTranslationGroupTestRepository groups;

    @BeforeEach
    void reset() {
        items.reset();
        groups.reset();
    }

    @Test
    void ownerSeesTranslationSectionOnContentEditPage() throws Exception {
        ContentItem item = AdminContentTestFixtures.completeDraft();
        items.add(item);

        mockMvc.perform(get("/admin/content/" + item.id().value() + "/edit").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Translations")))
                .andExpect(content().string(containsString("Create translation group")));
    }

    @Test
    void ownerCanCreateTranslationGroupWithCsrf() throws Exception {
        ContentItem item = AdminContentTestFixtures.completeDraft();
        items.add(item);

        mockMvc.perform(post("/admin/content/" + item.id().value() + "/translation-group")
                        .with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/content/*/edit?translationGroupCreated"));

        org.assertj.core.api.Assertions.assertThat(groups.contentItemBelongsToAnyGroup(item.id())).isTrue();
    }

    @Test
    void ownerCanAddTranslationEntryWithCsrf() throws Exception {
        ContentItem english = AdminContentTestFixtures.completeDraft();
        ContentItem turkish = draft(ContentLanguage.TR, "Turkish translation");
        items.add(english);
        items.add(turkish);
        TranslationGroupId groupId = createGroup(english.id());

        mockMvc.perform(post("/admin/translation-groups/" + groupId.value() + "/entries")
                        .with(owner()).with(csrf())
                        .param("contentItemId", turkish.id().value().toString())
                        .param("returnContentId", english.id().value().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/content/*/edit?translationEntryAdded"));

        org.assertj.core.api.Assertions.assertThat(
                groups.findById(groupId).orElseThrow().entries()).hasSize(2);
    }

    @Test
    void ownerCanRemoveTranslationEntryWithCsrf() throws Exception {
        ContentItem english = AdminContentTestFixtures.completeDraft();
        ContentItem turkish = draft(ContentLanguage.TR, "Turkish translation");
        items.add(english);
        items.add(turkish);
        TranslationGroupId groupId = createGroup(english.id());
        addEntry(groupId, turkish.id(), english.id());
        var entryId = groups.findById(groupId).orElseThrow().entries().stream()
                .filter(entry -> entry.contentItemId().equals(turkish.id()))
                .findFirst().orElseThrow().id();

        mockMvc.perform(post("/admin/translation-groups/" + groupId.value()
                        + "/entries/" + entryId.value() + "/remove")
                        .with(owner()).with(csrf())
                        .param("returnContentId", english.id().value().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/content/*/edit?translationEntryRemoved"));

        org.assertj.core.api.Assertions.assertThat(
                groups.findById(groupId).orElseThrow().entries()).hasSize(1);
    }

    @Test
    void postWithoutCsrfRejected() throws Exception {
        ContentItem item = AdminContentTestFixtures.completeDraft();
        items.add(item);

        mockMvc.perform(post("/admin/content/" + item.id().value() + "/translation-group").with(owner()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotMutateTranslationGroup() throws Exception {
        ContentItem item = AdminContentTestFixtures.completeDraft();
        items.add(item);

        mockMvc.perform(post("/admin/content/" + item.id().value() + "/translation-group").with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void nonOwnerCannotMutateTranslationGroup() throws Exception {
        ContentItem item = AdminContentTestFixtures.completeDraft();
        items.add(item);

        mockMvc.perform(post("/admin/content/" + item.id().value() + "/translation-group")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationErrorsRenderInAdminPage() throws Exception {
        ContentItem item = AdminContentTestFixtures.completeDraft();
        items.add(item);

        mockMvc.perform(get("/admin/content/" + item.id().value() + "/edit?translationError=ALREADY_IN_GROUP")
                        .with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already belongs to a translation group")));
    }

    @Test
    void adminPagesRemainNoStoreAndNoindex() throws Exception {
        ContentItem item = AdminContentTestFixtures.completeDraft();
        items.add(item);

        mockMvc.perform(get("/admin/content/" + item.id().value() + "/edit").with(owner()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().string(containsString("noindex,nofollow,noarchive")));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }

    private TranslationGroupId createGroup(ContentId contentId) throws Exception {
        mockMvc.perform(post("/admin/content/" + contentId.value() + "/translation-group")
                        .with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        return groups.findByContentItemId(contentId).orElseThrow().id();
    }

    private void addEntry(TranslationGroupId groupId, ContentId contentItemId, ContentId returnContentId)
            throws Exception {
        mockMvc.perform(post("/admin/translation-groups/" + groupId.value() + "/entries")
                        .with(owner()).with(csrf())
                        .param("contentItemId", contentItemId.value().toString())
                        .param("returnContentId", returnContentId.value().toString()))
                .andExpect(status().is3xxRedirection());
    }

    private static ContentItem draft(ContentLanguage language, String title) {
        ContentItem item = ContentItem.createDraft(
                ContentId.newId(), ContentType.ARTICLE, ContentVisibility.PRIVATE, language, CREATED);
        item.changeTitle(Title.of(title), CREATED.plusSeconds(1));
        return item;
    }
}
