package dev.persefonia.contentpublishing.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.query.PublicSeriesBySourceQuery;
import dev.persefonia.contentpublishing.application.query.PublicSeriesLookupResult;
import dev.persefonia.contentpublishing.application.support.InMemoryContentItemRepository;
import dev.persefonia.contentpublishing.application.support.InMemorySeriesRepository;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.ReadingTime;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.content.RendererVersion;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.content.Version;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesDescription;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntryId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSlug;
import dev.persefonia.contentpublishing.domain.model.series.SeriesTitle;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublicSeriesPageQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");

    private final InMemoryContentItemRepository items = new InMemoryContentItemRepository();
    private final InMemorySeriesRepository seriesRepository = new InMemorySeriesRepository();
    private final PublicSeriesPageQueryService service =
            new PublicSeriesPageQueryService(items, seriesRepository, new ContentPublicRouteFactory());

    @Test
    void publicSeriesPageRendersEligibleEntriesInPositionOrder() {
        Series series = series("spring-notes", ContentLanguage.EN);
        ContentItem first = content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.EN, true, false, "first");
        ContentItem second = content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.EN, true, false, "second");
        addEntry(series, first);
        addEntry(series, second);
        seriesRepository.save(series);
        items.add(second);
        items.add(first);

        PublicSeriesLookupResult result = service.lookup(query(series, "spring-notes", ContentLanguage.EN));

        assertThat(result).isInstanceOfSatisfying(PublicSeriesLookupResult.Found.class, found ->
                assertThat(found.page().entries())
                        .extracting(entry -> entry.position() + ":" + entry.publicUrl())
                        .containsExactly("1:/en/articles/first", "2:/en/articles/second"));
    }

    @Test
    void publicSeriesPageRendersEmptyStateForActiveSeriesWithoutEligibleEntries() {
        Series series = series("empty", ContentLanguage.TR);
        seriesRepository.save(series);

        PublicSeriesLookupResult result = service.lookup(query(series, "empty", ContentLanguage.TR));

        assertThat(result).isInstanceOfSatisfying(PublicSeriesLookupResult.Found.class, found ->
                assertThat(found.page().entries()).isEmpty());
    }

    @Test
    void missingStaleAndArchivedSeriesReturnNotFound() {
        Series current = series("current", ContentLanguage.EN);
        Series archived = series("archived", ContentLanguage.EN);
        archived.archive(NOW.plusSeconds(5));
        seriesRepository.save(current);
        seriesRepository.save(archived);

        assertThat(service.lookup(new PublicSeriesBySourceQuery(SeriesId.newId().value(), ContentLanguage.EN, "missing")))
                .isInstanceOf(PublicSeriesLookupResult.NotFound.class);
        assertThat(service.lookup(query(current, "old", ContentLanguage.EN)))
                .isInstanceOf(PublicSeriesLookupResult.NotFound.class);
        assertThat(service.lookup(query(current, "current", ContentLanguage.TR)))
                .isInstanceOf(PublicSeriesLookupResult.NotFound.class);
        assertThat(service.lookup(query(archived, "archived", ContentLanguage.EN)))
                .isInstanceOf(PublicSeriesLookupResult.NotFound.class);
    }

    @Test
    void seriesPageDoesNotListIneligibleEntries() {
        Series series = series("visibility", ContentLanguage.TR);
        List<ContentItem> content = List.of(
                content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.TR, true, false, "listed"),
                content(ContentStatus.PUBLISHED, ContentVisibility.UNLISTED, ContentLanguage.TR, true, false, "unlisted"),
                content(ContentStatus.PUBLISHED, ContentVisibility.PRIVATE, ContentLanguage.TR, true, false, "private"),
                content(ContentStatus.DRAFT, ContentVisibility.PUBLIC, ContentLanguage.TR, true, false, "draft"),
                content(ContentStatus.UNPUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.TR, true, false, "unpublished"),
                content(ContentStatus.ARCHIVED, ContentVisibility.PUBLIC, ContentLanguage.TR, true, false, "archived"),
                content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.EN, true, false, "english"),
                content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.TR, false, false, "no-snapshot"),
                content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.TR, true, true, "stale"));
        content.forEach(item -> {
            addEntry(series, item);
            items.add(item);
        });
        seriesRepository.save(series);

        PublicSeriesLookupResult result = service.lookup(query(series, "visibility", ContentLanguage.TR));

        assertThat(result).isInstanceOfSatisfying(PublicSeriesLookupResult.Found.class, found ->
                assertThat(found.page().entries())
                        .singleElement()
                        .satisfies(entry -> assertThat(entry.publicUrl()).isEqualTo("/tr/articles/listed")));
    }

    private static Series series(String slug, ContentLanguage language) {
        return Series.create(
                SeriesId.newId(),
                language,
                SeriesSlug.of(slug),
                SeriesTitle.of("Series " + slug),
                SeriesDescription.optional("Description " + slug).orElseThrow(),
                NOW);
    }

    private static void addEntry(Series series, ContentItem item) {
        series.addEntry(SeriesEntryId.newId(), item.id(), NOW.plusSeconds(series.entries().size() + 1L));
    }

    private static PublicSeriesBySourceQuery query(Series series, String slug, ContentLanguage language) {
        return new PublicSeriesBySourceQuery(series.id().value(), language, slug);
    }

    private static ContentItem content(
            ContentStatus status,
            ContentVisibility visibility,
            ContentLanguage language,
            boolean snapshot,
            boolean stale,
            String slug) {
        String currentPath = "/" + language.name().toLowerCase() + "/articles/" + slug;
        return ContentItem.rehydrate(
                ContentId.newId(),
                ContentType.ARTICLE,
                status,
                visibility,
                language,
                Slug.of(slug),
                Title.of(slug),
                Summary.of(slug + " summary"),
                MarkdownSource.of("# " + slug),
                ContentMetadata.withCanonicalPath(CanonicalPath.of(stale ? currentPath + "-old" : currentPath)),
                snapshot ? snapshot() : null,
                Set.of(),
                status == ContentStatus.DRAFT ? null : NOW,
                status == ContentStatus.UNPUBLISHED || status == ContentStatus.ARCHIVED ? NOW.plusSeconds(1) : null,
                NOW.minusSeconds(60),
                NOW.plusSeconds(2),
                Version.initial());
    }

    private static ContentRenderSnapshot snapshot() {
        return ContentRenderSnapshot.of(
                RenderedHtml.sanitized("<p>rendered</p>"),
                NOW,
                RendererVersion.of("test"),
                ReadingTime.minutes(1),
                false,
                List.of());
    }
}
