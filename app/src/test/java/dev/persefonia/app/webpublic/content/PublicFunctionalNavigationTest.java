package dev.persefonia.app.webpublic.content;

import dev.persefonia.app.webpublic.series.PublicSeriesTestRepository;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntryId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSlug;
import dev.persefonia.contentpublishing.domain.model.series.SeriesTitle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Import(PublicNavigationMvcTestConfiguration.class)
class PublicFunctionalNavigationTest extends PublicContentMvcTestSupport {
    @Autowired PublicSeriesTestRepository series;
    @Autowired dev.persefonia.app.webpublic.tags.PublicTagTestRepository tags;

    @Test
    void architectureLinkRequiresBothPublishedListedSourceAndCurrentPublicRoute() throws Exception {
        addProjected(PublicContentTestItems.publishedPublic(ContentType.ARTICLE, ContentLanguage.TR, "articles", "reader"), "/tr/articles/reader");
        var architecture = PublicContentTestItems.publishedPublic(ContentType.PAGE, ContentLanguage.TR, "pages", "architecture");
        items.add(architecture);
        assertThat(body("/tr/articles/reader")).doesNotContain("href=\"/tr/pages/architecture\"");
        routes.addFound("/tr/pages/architecture", architecture.id().value());
        assertThat(body("/tr/articles/reader")).contains("href=\"/tr/pages/architecture\">Architecture</a>");
        architecture.changeVisibility(ContentVisibility.UNLISTED, PublicContentTestItems.NOW.plusSeconds(2));
        assertThat(body("/tr/articles/reader")).doesNotContain("href=\"/tr/pages/architecture\"");
        architecture.changeVisibility(ContentVisibility.PUBLIC, PublicContentTestItems.NOW.plusSeconds(3));
        architecture.unpublish(PublicContentTestItems.NOW.plusSeconds(4));
        assertThat(body("/tr/articles/reader")).doesNotContain("href=\"/tr/pages/architecture\"");
    }

    @Test
    void contentLinksOnlyToActiveContainingSeriesWithResolvedPublicRoutes() throws Exception {
        series.reset();
        var article = PublicContentTestItems.publishedPublic(ContentType.ARTICLE, ContentLanguage.TR, "articles", "navigation");
        addProjected(article, "/tr/articles/navigation");
        var collection = Series.create(SeriesId.newId(), ContentLanguage.TR, SeriesSlug.of("system-notes"),
                SeriesTitle.of("System notes"), null, PublicContentTestItems.NOW);
        collection.addEntry(SeriesEntryId.newId(), article.id(), PublicContentTestItems.NOW.plusSeconds(1));
        series.add(collection);
        assertThat(body("/tr/articles/navigation")).doesNotContain("href=\"/tr/series/system-notes\"");
        routes.addSeriesFound("/tr/series/system-notes", collection.id().value());
        assertThat(body("/tr/articles/navigation")).contains("href=\"/tr/series/system-notes\">System notes</a>");
        collection.archive(PublicContentTestItems.NOW.plusSeconds(2));
        assertThat(body("/tr/articles/navigation")).doesNotContain("href=\"/tr/series/system-notes\"");
    }

    @Test
    void contentTagLinksRequireActiveVocabularyAndAResolvedRoute() throws Exception {
        tags.reset();
        var tag = dev.persefonia.taxonomy.domain.model.Tag.create(
                dev.persefonia.taxonomy.domain.model.TagId.newId(),
                dev.persefonia.taxonomy.domain.model.TagName.of("Systems"),
                dev.persefonia.taxonomy.domain.model.NormalizedTagName.ofCanonical("systems"),
                dev.persefonia.taxonomy.domain.model.TagSlug.ofCanonical("systems"),
                dev.persefonia.taxonomy.domain.model.TagDescription.empty(), PublicContentTestItems.NOW);
        tags.add(tag);
        var article = PublicContentTestItems.publishedPublic(ContentType.ARTICLE, ContentLanguage.TR, "articles", "tagged");
        article.replaceTags(java.util.Set.of(dev.persefonia.contentpublishing.domain.content.TagId.from(tag.id().value())),
                PublicContentTestItems.NOW.plusSeconds(2));
        addProjected(article, "/tr/articles/tagged");
        assertThat(body("/tr/articles/tagged")).doesNotContain("href=\"/tr/tags/systems\"");
        routes.addTagFound("/tr/tags/systems", tag.id().value());
        assertThat(body("/tr/articles/tagged")).contains("href=\"/tr/tags/systems\">Systems</a>");
        tag.archive(PublicContentTestItems.NOW.plusSeconds(3));
        assertThat(body("/tr/articles/tagged")).doesNotContain("href=\"/tr/tags/systems\"");
    }

    private String body(String path) throws Exception {
        var response = mockMvc.perform(get(path)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return response.getContentAsString();
    }
}
