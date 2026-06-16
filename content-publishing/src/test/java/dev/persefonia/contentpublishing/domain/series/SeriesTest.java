package dev.persefonia.contentpublishing.domain.series;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.Version;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesDescription;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntry;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntryId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntryPosition;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSlug;
import dev.persefonia.contentpublishing.domain.model.series.SeriesStatus;
import dev.persefonia.contentpublishing.domain.model.series.SeriesTitle;
import dev.persefonia.contentpublishing.domain.model.series.SeriesValidationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeriesTest {
    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-06-15T11:00:00Z");

    @Test
    void createsActiveSeries() {
        Series series = series();

        assertThat(series.status()).isEqualTo(SeriesStatus.ACTIVE);
        assertThat(series.entries()).isEmpty();
        assertThat(series.version().value()).isZero();
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> SeriesTitle.of(" "))
                .isInstanceOf(SeriesValidationException.class);
    }

    @Test
    void rejectsInvalidSlug() {
        assertThatThrownBy(() -> SeriesSlug.of("Bad Slug"))
                .isInstanceOf(SeriesValidationException.class);
    }

    @Test
    void updatesMetadata() {
        Series series = series();

        series.updateMetadata(SeriesTitle.of("Updated"), SeriesSlug.of("updated"), null, LATER);

        assertThat(series.title().value()).isEqualTo("Updated");
        assertThat(series.slug().value()).isEqualTo("updated");
        assertThat(series.updatedAt()).isEqualTo(LATER);
        assertThat(series.version().value()).isEqualTo(1);
    }

    @Test
    void archivesSeriesIdempotently() {
        Series series = series();

        series.archive(LATER);
        series.archive(LATER.plusSeconds(1));

        assertThat(series.status()).isEqualTo(SeriesStatus.ARCHIVED);
        assertThat(series.version().value()).isEqualTo(1);
    }

    @Test
    void rejectsMutationOfArchivedSeries() {
        Series series = series();
        series.archive(LATER);

        assertThatThrownBy(() -> series.addEntry(SeriesEntryId.newId(), ContentId.newId(), LATER.plusSeconds(1)))
                .isInstanceOf(SeriesValidationException.class);
    }

    @Test
    void addsEntryAtEnd() {
        Series series = series();
        series.addEntry(SeriesEntryId.newId(), ContentId.newId(), LATER);
        SeriesEntry second = series.addEntry(SeriesEntryId.newId(), ContentId.newId(), LATER.plusSeconds(1));

        assertThat(second.position().value()).isEqualTo(2);
    }

    @Test
    void rejectsDuplicateContentItemInSameSeries() {
        Series series = series();
        ContentId contentId = ContentId.newId();
        series.addEntry(SeriesEntryId.newId(), contentId, LATER);

        assertThatThrownBy(() -> series.addEntry(SeriesEntryId.newId(), contentId, LATER.plusSeconds(1)))
                .isInstanceOf(SeriesValidationException.class);
    }

    @Test
    void removesEntryAndNormalizesPositions() {
        Series series = series();
        SeriesEntry first = series.addEntry(SeriesEntryId.newId(), ContentId.newId(), LATER);
        series.addEntry(SeriesEntryId.newId(), ContentId.newId(), LATER);
        series.removeEntry(first.id(), LATER.plusSeconds(1));

        assertThat(series.entries()).extracting(entry -> entry.position().value()).containsExactly(1);
    }

    @Test
    void reordersEntries() {
        Series series = series();
        SeriesEntry first = series.addEntry(SeriesEntryId.newId(), ContentId.newId(), LATER);
        SeriesEntry second = series.addEntry(SeriesEntryId.newId(), ContentId.newId(), LATER);

        series.reorderEntries(List.of(second.id(), first.id()), LATER.plusSeconds(1));

        assertThat(series.entries()).extracting(SeriesEntry::id).containsExactly(second.id(), first.id());
        assertThat(series.entries()).extracting(entry -> entry.position().value()).containsExactly(1, 2);
    }

    @Test
    void rejectsDuplicatePositionsOnRehydrate() {
        assertThatThrownBy(() -> rehydrate(List.of(entry(1), entry(1))))
                .isInstanceOf(SeriesValidationException.class);
    }

    @Test
    void rejectsNonPositivePositionsOnRehydrate() {
        assertThatThrownBy(() -> new SeriesEntry(SeriesEntryId.newId(), ContentId.newId(), SeriesEntryPosition.of(0), NOW))
                .isInstanceOf(SeriesValidationException.class);
    }

    @Test
    void rejectsNonContiguousPositionsOnRehydrate() {
        assertThatThrownBy(() -> rehydrate(List.of(entry(1), entry(3))))
                .isInstanceOf(SeriesValidationException.class);
    }

    @Test
    void rehydrateSortsEntriesByPosition() {
        SeriesEntry first = entry(1);
        SeriesEntry second = entry(2);

        Series series = rehydrate(List.of(second, first));

        assertThat(series.entries()).containsExactly(first, second);
    }

    private static Series series() {
        return Series.create(
                SeriesId.newId(),
                ContentLanguage.EN,
                SeriesSlug.of("learning-path"),
                SeriesTitle.of("Learning Path"),
                SeriesDescription.optional("A useful path").orElseThrow(),
                NOW);
    }

    private static Series rehydrate(List<SeriesEntry> entries) {
        return Series.rehydrate(
                SeriesId.newId(),
                ContentLanguage.EN,
                SeriesSlug.of("learning-path"),
                SeriesTitle.of("Learning Path"),
                null,
                SeriesStatus.ACTIVE,
                entries,
                NOW,
                NOW,
                Version.initial());
    }

    private static SeriesEntry entry(int position) {
        return new SeriesEntry(
                SeriesEntryId.newId(),
                ContentId.newId(),
                SeriesEntryPosition.of(position),
                NOW);
    }
}
