package dev.persefonia.app.webpublic.series;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.InMemoryPublicRouteResolver;
import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
import dev.persefonia.app.webpublic.content.PublicContentTestItems;
import dev.persefonia.app.webpublic.content.PublicContentTestRepository;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesDescription;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntryId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSlug;
import dev.persefonia.contentpublishing.domain.model.series.SeriesTitle;
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
@Import({PublicContentTestConfiguration.class, PublicSeriesTestConfiguration.class})
@ActiveProfiles({"test", "public-content-mvc-test", "public-series-mvc-test"})
class PublicSeriesPageControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired PublicSeriesTestRepository seriesRepository;
    @Autowired PublicContentTestRepository items;
    @Autowired InMemoryPublicRouteResolver routes;

    @BeforeEach
    void reset() {
        seriesRepository.reset();
        items.reset();
        routes.clear();
    }

    @Test
    void anonymousSeriesPageRendersEligibleEntriesNoindexCanonicalAndPublicCache() throws Exception {
        Series series = series("spring-boot-notes", ContentLanguage.EN);
        ContentItem first = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.EN, "articles", "first");
        ContentItem second = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.EN, "articles", "second");
        addEntry(series, first);
        addEntry(series, second);
        seriesRepository.add(series);
        items.add(second);
        items.add(first);
        routes.addSeriesFound("/en/series/spring-boot-notes", series.id().value());

        mockMvc.perform(get("/en/series/spring-boot-notes"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(containsString(
                        "<link rel=\"canonical\" href=\"https://0xmillennium.dev/en/series/spring-boot-notes\">")))
                .andExpect(content().string(containsString("/en/articles/first")))
                .andExpect(content().string(containsString("/en/articles/second")))
                .andExpect(content().string(not(containsString("/en/series\""))));
    }

    @Test
    void existingProjectedSeriesWithoutEligibleContentRendersEmptyState() throws Exception {
        Series series = series("empty", ContentLanguage.TR);
        seriesRepository.add(series);
        routes.addSeriesFound("/tr/series/empty", series.id().value());

        mockMvc.perform(get("/tr/series/empty"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No public content is currently available for this series.")));
    }

    @Test
    void missingProjectionMissingSeriesStaleProjectionAndArchivedSeriesReturnSafeNotFound() throws Exception {
        mockMvc.perform(get("/en/series/missing"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        routes.addSeriesFound("/en/series/orphan", java.util.UUID.randomUUID());
        mockMvc.perform(get("/en/series/orphan"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("source_entity_id"))));

        Series current = series("current", ContentLanguage.EN);
        seriesRepository.add(current);
        routes.addSeriesFound("/en/series/old", current.id().value());
        mockMvc.perform(get("/en/series/old"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("private")));

        Series archived = series("archived", ContentLanguage.EN);
        archived.archive(PublicContentTestItems.NOW.plusSeconds(3));
        seriesRepository.add(archived);
        routes.addSeriesFound("/en/series/archived", archived.id().value());
        mockMvc.perform(get("/en/series/archived"))
                .andExpect(status().isNotFound());
    }

    @Test
    void seriesIndexInvalidLanguageInvalidSlugAndTrailingSlashAreNotPublic() throws Exception {
        mockMvc.perform(get("/en/series")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/de/series/spring")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/en/series/Spring")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/en/series/spring/")).andExpect(status().is4xxClientError());
    }

    @Test
    void seriesPageDoesNotListIneligibleContent() throws Exception {
        Series series = series("visibility", ContentLanguage.TR);
        ContentItem listed = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "listed");
        ContentItem unlisted = PublicContentTestItems.publishedUnlisted("unlisted");
        ContentItem privateContent = PublicContentTestItems.publishedPrivate("private");
        ContentItem draft = PublicContentTestItems.draft("draft");
        ContentItem unpublished = PublicContentTestItems.unpublished("unpublished");
        ContentItem archived = PublicContentTestItems.archived("archived");
        ContentItem differentLanguage = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.EN, "articles", "english");
        ContentItem noSnapshot = PublicContentTestItems.publishedWithoutSnapshot("no-snapshot");
        ContentItem stale = stale(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "stale"));
        for (ContentItem item : java.util.List.of(
                listed, unlisted, privateContent, draft, unpublished, archived, differentLanguage, noSnapshot, stale)) {
            addEntry(series, item);
            items.add(item);
        }
        seriesRepository.add(series);
        routes.addSeriesFound("/tr/series/visibility", series.id().value());

        mockMvc.perform(get("/tr/series/visibility"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/tr/articles/listed")))
                .andExpect(content().string(not(containsString("/tr/articles/unlisted"))))
                .andExpect(content().string(not(containsString("/tr/articles/private"))))
                .andExpect(content().string(not(containsString("/tr/articles/draft"))))
                .andExpect(content().string(not(containsString("/tr/articles/unpublished"))))
                .andExpect(content().string(not(containsString("/tr/articles/archived"))))
                .andExpect(content().string(not(containsString("/en/articles/english"))))
                .andExpect(content().string(not(containsString("/tr/articles/no-snapshot"))))
                .andExpect(content().string(not(containsString("/tr/articles/stale"))));
    }

    private static Series series(String slug, ContentLanguage language) {
        return Series.create(
                SeriesId.newId(),
                language,
                SeriesSlug.of(slug),
                SeriesTitle.of("Series " + slug),
                SeriesDescription.optional("Series description").orElseThrow(),
                PublicContentTestItems.NOW);
    }

    private static void addEntry(Series series, ContentItem item) {
        series.addEntry(
                SeriesEntryId.newId(),
                item.id(),
                PublicContentTestItems.NOW.plusSeconds(series.entries().size() + 1L));
    }

    private static ContentItem stale(ContentItem item) {
        item.changeMetadata(
                ContentMetadata.withCanonicalPath(CanonicalPath.of(item.metadata().canonicalPath().orElseThrow().value() + "-old")),
                PublicContentTestItems.NOW.plusSeconds(5));
        return item;
    }
}
