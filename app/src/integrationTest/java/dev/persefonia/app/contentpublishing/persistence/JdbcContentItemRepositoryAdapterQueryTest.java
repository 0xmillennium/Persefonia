package dev.persefonia.app.contentpublishing.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Slug;

import org.junit.jupiter.api.Test;

class JdbcContentItemRepositoryAdapterQueryTest extends ContentPublishingRepositoryTestDatabase {
    @Test
    void findsBySlugTypeAndLanguageRegardlessOfStatus() {
        var archived = contentItems.save(ContentItemRepositoryTestFixtures.archived("archived-lookup"));

        assertThat(contentItems.findBySlugAndTypeAndLanguage(
                Slug.ofCanonical("archived-lookup"), ContentType.ARTICLE, ContentLanguage.EN))
                .map(item -> item.id())
                .contains(archived.id());
        assertThat(contentItems.findBySlugAndTypeAndLanguage(
                Slug.ofCanonical("missing-lookup"), ContentType.ARTICLE, ContentLanguage.EN))
                .isEmpty();
    }

    @Test
    void publishedRouteQueryIncludesOnlyPublishedPublicOrUnlistedContent() {
        var publicItem = contentItems.save(ContentItemRepositoryTestFixtures.published("public-route", ContentVisibility.PUBLIC));
        var unlistedItem = contentItems.save(ContentItemRepositoryTestFixtures.published("unlisted-route", ContentVisibility.UNLISTED));
        contentItems.save(ContentItemRepositoryTestFixtures.completeDraft("draft-route"));
        contentItems.save(ContentItemRepositoryTestFixtures.unpublished("unpublished-route"));
        contentItems.save(ContentItemRepositoryTestFixtures.archived("archived-route"));
        contentItems.save(ContentItemRepositoryTestFixtures.published("private-route", ContentVisibility.PRIVATE));

        assertThat(contentItems.findPublishedByRoute(ContentType.ARTICLE, Slug.ofCanonical("public-route"), ContentLanguage.EN))
                .map(item -> item.id())
                .contains(publicItem.id());
        assertThat(contentItems.findPublishedByRoute(ContentType.ARTICLE, Slug.ofCanonical("unlisted-route"), ContentLanguage.EN))
                .map(item -> item.id())
                .contains(unlistedItem.id());
        assertThat(contentItems.findPublishedByRoute(ContentType.ARTICLE, Slug.ofCanonical("draft-route"), ContentLanguage.EN))
                .isEmpty();
        assertThat(contentItems.findPublishedByRoute(ContentType.ARTICLE, Slug.ofCanonical("unpublished-route"), ContentLanguage.EN))
                .isEmpty();
        assertThat(contentItems.findPublishedByRoute(ContentType.ARTICLE, Slug.ofCanonical("archived-route"), ContentLanguage.EN))
                .isEmpty();
        assertThat(contentItems.findPublishedByRoute(ContentType.ARTICLE, Slug.ofCanonical("private-route"), ContentLanguage.EN))
                .isEmpty();
    }

    @Test
    void draftAndStatusQueriesReturnOnlyMatchingRows() {
        var draft = contentItems.save(ContentItemRepositoryTestFixtures.completeDraft("draft-list"));
        var published = contentItems.save(ContentItemRepositoryTestFixtures.published("published-list", ContentVisibility.PUBLIC));

        assertThat(contentItems.findDrafts()).extracting(item -> item.id()).containsExactly(draft.id());
        assertThat(contentItems.findByStatus(ContentStatus.PUBLISHED)).extracting(item -> item.id()).containsExactly(published.id());
    }

    @Test
    void slugNamespaceExistsByTypeLanguageAndSlug() {
        contentItems.save(ContentItemRepositoryTestFixtures.completeDraft("namespace"));
        contentItems.save(ContentItemRepositoryTestFixtures.completeDraft("namespace", ContentType.PAGE, ContentLanguage.EN));
        contentItems.save(ContentItemRepositoryTestFixtures.completeDraft("namespace", ContentType.ARTICLE, ContentLanguage.TR));

        assertThat(contentItems.existsSlugInNamespace(ContentType.ARTICLE, ContentLanguage.EN, Slug.ofCanonical("namespace")))
                .isTrue();
        assertThat(contentItems.existsSlugInNamespace(ContentType.RESEARCH, ContentLanguage.EN, Slug.ofCanonical("namespace")))
                .isFalse();
        assertThat(contentItems.existsSlugInNamespace(ContentType.NOTE, ContentLanguage.TR, Slug.ofCanonical("namespace")))
                .isFalse();
    }
}
