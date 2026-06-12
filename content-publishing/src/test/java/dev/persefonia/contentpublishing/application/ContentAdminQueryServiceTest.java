package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.EDITOR;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.NOW;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.exception.ContentCommandRejectedException;
import dev.persefonia.contentpublishing.application.service.ContentAdminQueryService;
import dev.persefonia.contentpublishing.application.support.InMemoryContentItemRepository;
import dev.persefonia.contentpublishing.application.support.TestContentAuthorizationPolicy;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import org.junit.jupiter.api.Test;

class ContentAdminQueryServiceTest {
    private final InMemoryContentItemRepository items = new InMemoryContentItemRepository();
    private final ContentAdminQueryService queries =
            new ContentAdminQueryService(items, new TestContentAuthorizationPolicy());

    @Test
    void listsManageableContentForOwnerAndExcludesArchivedByDefault() {
        var draft = ContentItemTestFixtures.completeDraft();
        var unpublished = ContentItemTestFixtures.published(ContentVisibility.PRIVATE);
        unpublished.unpublish(NOW);
        var published = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        var archived = ContentItemTestFixtures.completeDraft();
        archived.archive(NOW);
        items.add(draft);
        items.add(unpublished);
        items.add(published);
        items.add(archived);

        assertThat(queries.listManageableContent(OWNER))
                .extracting(item -> item.status())
                .containsExactlyInAnyOrder(ContentStatus.DRAFT, ContentStatus.UNPUBLISHED, ContentStatus.PUBLISHED);
        assertThatThrownBy(() -> queries.listManageableContent(EDITOR)).isInstanceOf(SecurityException.class);
    }

    @Test
    void returnsSafeEditReadModelAndRejectsPublishedContent() {
        var draft = ContentItemTestFixtures.completeDraft();
        var published = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        items.add(draft);
        items.add(published);

        var result = queries.getContentForEditing(OWNER, draft.id());

        assertThat(result.title()).contains("Content baseline");
        assertThat(result.markdownSource()).contains("# Content baseline");
        assertThatThrownBy(() -> queries.getContentForEditing(OWNER, published.id()))
                .isInstanceOf(ContentCommandRejectedException.class);
        assertThat(queries.getContentForAdmin(OWNER, published.id()).status())
                .isEqualTo(ContentStatus.PUBLISHED);
        assertThatThrownBy(() -> queries.getContentForAdmin(EDITOR, published.id()))
                .isInstanceOf(SecurityException.class);
    }
}
