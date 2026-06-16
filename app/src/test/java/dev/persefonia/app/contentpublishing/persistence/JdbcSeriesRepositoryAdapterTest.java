package dev.persefonia.app.contentpublishing.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Version;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesDescription;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntryId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSlug;
import dev.persefonia.contentpublishing.domain.model.series.SeriesStatus;
import dev.persefonia.contentpublishing.domain.model.series.SeriesTitle;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

class JdbcSeriesRepositoryAdapterTest extends ContentPublishingRepositoryTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");

    @Test
    void persistsAndLoadsSeries() {
        Series saved = seriesRepository.save(series("repository-path", ContentLanguage.EN));

        Series loaded = seriesRepository.findById(saved.id()).orElseThrow();
        assertThat(loaded.slug().value()).isEqualTo("repository-path");
        assertThat(loaded.status()).isEqualTo(SeriesStatus.ACTIVE);
    }

    @Test
    void persistsEntries() {
        ContentId first = saveContent("series-entry-one", ContentLanguage.EN);
        ContentId second = saveContent("series-entry-two", ContentLanguage.EN);
        Series series = series("entries-path", ContentLanguage.EN);
        series.addEntry(SeriesEntryId.newId(), first, NOW.plusSeconds(1));
        series.addEntry(SeriesEntryId.newId(), second, NOW.plusSeconds(2));

        Series loaded = seriesRepository.findById(seriesRepository.save(series).id()).orElseThrow();

        assertThat(loaded.entries()).hasSize(2);
        assertThat(loaded.entries()).extracting(entry -> entry.contentItemId()).containsExactly(first, second);
    }

    @Test
    void findsByLanguageAndSlug() {
        Series saved = seriesRepository.save(series("find-series", ContentLanguage.TR));

        assertThat(seriesRepository.findByLanguageAndSlug(ContentLanguage.TR, SeriesSlug.of("find-series")).orElseThrow().id())
                .isEqualTo(saved.id());
        assertThat(seriesRepository.existsByLanguageAndSlug(ContentLanguage.TR, SeriesSlug.of("find-series"))).isTrue();
    }

    @Test
    void uniqueLanguageSlugEnforced() {
        seriesRepository.save(series("unique-series", ContentLanguage.EN));

        assertThatThrownBy(() -> seriesRepository.save(series("unique-series", ContentLanguage.EN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueEntryContentWithinSeriesEnforced() {
        ContentId content = saveContent("unique-entry-content", ContentLanguage.EN);

        assertThatThrownBy(() -> Series.rehydrate(
                SeriesId.newId(),
                ContentLanguage.EN,
                SeriesSlug.of("unique-entry-content-series"),
                SeriesTitle.of("Unique Entry Content"),
                null,
                SeriesStatus.ACTIVE,
                java.util.List.of(
                        new dev.persefonia.contentpublishing.domain.model.series.SeriesEntry(
                                SeriesEntryId.newId(), content,
                                dev.persefonia.contentpublishing.domain.model.series.SeriesEntryPosition.of(1), NOW),
                        new dev.persefonia.contentpublishing.domain.model.series.SeriesEntry(
                                SeriesEntryId.newId(), content,
                                dev.persefonia.contentpublishing.domain.model.series.SeriesEntryPosition.of(2), NOW)),
                NOW,
                NOW,
                Version.initial()))
                .isInstanceOf(dev.persefonia.contentpublishing.domain.model.series.SeriesValidationException.class);
    }

    @Test
    void uniqueEntryPositionWithinSeriesEnforced() {
        ContentId first = saveContent("unique-position-one", ContentLanguage.EN);
        ContentId second = saveContent("unique-position-two", ContentLanguage.EN);
        assertThatThrownBy(() -> Series.rehydrate(
                SeriesId.newId(),
                ContentLanguage.EN,
                SeriesSlug.of("unique-entry-position-series"),
                SeriesTitle.of("Unique Entry Position"),
                null,
                SeriesStatus.ACTIVE,
                java.util.List.of(
                        new dev.persefonia.contentpublishing.domain.model.series.SeriesEntry(
                                SeriesEntryId.newId(), first,
                                dev.persefonia.contentpublishing.domain.model.series.SeriesEntryPosition.of(1), NOW),
                        new dev.persefonia.contentpublishing.domain.model.series.SeriesEntry(
                                SeriesEntryId.newId(), second,
                                dev.persefonia.contentpublishing.domain.model.series.SeriesEntryPosition.of(1), NOW)),
                NOW,
                NOW,
                Version.initial())).isInstanceOf(dev.persefonia.contentpublishing.domain.model.series.SeriesValidationException.class);
    }

    @Test
    void sameContentCanAppearInDifferentSeries() {
        ContentId content = saveContent("multi-series-content", ContentLanguage.EN);
        Series first = series("first-series", ContentLanguage.EN);
        Series second = series("second-series", ContentLanguage.EN);
        first.addEntry(SeriesEntryId.newId(), content, NOW.plusSeconds(1));
        second.addEntry(SeriesEntryId.newId(), content, NOW.plusSeconds(1));

        seriesRepository.save(first);
        seriesRepository.save(second);

        assertThat(seriesRepository.findAllForAdmin()).hasSize(2);
    }

    @Test
    void optimisticVersionHandledAccordingToProjectPattern() {
        SeriesId id = seriesRepository.save(series("optimistic-series", ContentLanguage.EN)).id();
        Series current = seriesRepository.findById(id).orElseThrow();
        Series stale = seriesRepository.findById(id).orElseThrow();

        current.updateMetadata(
                SeriesTitle.of("Current"),
                SeriesSlug.of("optimistic-current"),
                null,
                NOW.plusSeconds(1));
        seriesRepository.save(current);

        stale.updateMetadata(
                SeriesTitle.of("Stale"),
                SeriesSlug.of("optimistic-stale"),
                null,
                NOW.plusSeconds(2));
        assertThatThrownBy(() -> seriesRepository.save(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private ContentId saveContent(String slug, ContentLanguage language) {
        return contentItems.save(ContentItemRepositoryTestFixtures.completeDraft(slug, ContentType.ARTICLE, language)).id();
    }

    private static Series series(String slug, ContentLanguage language) {
        return Series.create(
                SeriesId.newId(),
                language,
                SeriesSlug.of(slug),
                SeriesTitle.of("Title " + slug),
                SeriesDescription.optional("Description " + slug).orElseThrow(),
                NOW);
    }
}
