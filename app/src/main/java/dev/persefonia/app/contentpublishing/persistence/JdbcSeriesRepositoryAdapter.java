package dev.persefonia.app.contentpublishing.persistence;

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
import dev.persefonia.contentpublishing.domain.model.series.SeriesSummary;
import dev.persefonia.contentpublishing.domain.model.series.SeriesTitle;
import dev.persefonia.contentpublishing.domain.model.series.port.SeriesRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcSeriesRepositoryAdapter implements SeriesRepository {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final ObjectProvider<TransactionTemplate> transactions;

    JdbcSeriesRepositoryAdapter(
            ObjectProvider<NamedParameterJdbcTemplate> jdbc,
            ObjectProvider<TransactionTemplate> transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public Series save(Series series) {
        Objects.requireNonNull(series, "series");
        return transactionTemplate().execute(status -> {
            Optional<Long> currentVersion = currentVersion(series.id());
            if (currentVersion.isEmpty()) {
                insertSeries(series);
            } else {
                updateSeries(series, currentVersion.get());
            }
            replaceEntries(series);
            return findById(series.id()).orElseThrow(() -> new ContentPublishingPersistenceException(
                    "Saved series could not be reloaded: " + series.id().value()));
        });
    }

    @Override
    public Optional<Series> findById(SeriesId id) {
        Objects.requireNonNull(id, "id");
        return loadSeries("id = :id", Map.of("id", id.value()));
    }

    @Override
    public Optional<Series> findByLanguageAndSlug(ContentLanguage language, SeriesSlug slug) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
        return loadSeries(
                "language = :language AND slug = :slug",
                Map.of("language", language.name(), "slug", slug.value()));
    }

    @Override
    public boolean existsByLanguageAndSlug(ContentLanguage language, SeriesSlug slug) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
        Long count = jdbc().queryForObject("""
                SELECT count(*)
                FROM publishing.series
                WHERE language = :language AND slug = :slug
                """, Map.of("language", language.name(), "slug", slug.value()), Long.class);
        return count != null && count > 0;
    }

    @Override
    public List<SeriesSummary> findAllForAdmin() {
        return jdbc().query("""
                SELECT series.id,
                       series.language,
                       series.slug,
                       series.title,
                       series.status,
                       series.updated_at,
                       count(series_entries.id) AS entry_count
                FROM publishing.series series
                LEFT JOIN publishing.series_entries series_entries
                  ON series_entries.series_id = series.id
                GROUP BY series.id
                ORDER BY series.updated_at DESC, series.title ASC
                """, (resultSet, rowNumber) -> new SeriesSummary(
                SeriesId.from(resultSet.getObject("id", UUID.class)),
                ContentLanguage.valueOf(resultSet.getString("language")),
                SeriesSlug.of(resultSet.getString("slug")),
                SeriesTitle.of(resultSet.getString("title")),
                SeriesStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("entry_count"),
                resultSet.getTimestamp("updated_at").toInstant()));
    }

    private Optional<Long> currentVersion(SeriesId id) {
        List<Long> versions = jdbc().query("""
                SELECT version
                FROM publishing.series
                WHERE id = :id
                """, Map.of("id", id.value()), (resultSet, rowNumber) -> resultSet.getLong("version"));
        return versions.stream().findFirst();
    }

    private void insertSeries(Series series) {
        jdbc().update("""
                INSERT INTO publishing.series (
                    id, language, slug, title, description, status, created_at, updated_at, version
                ) VALUES (
                    :id, :language, :slug, :title, :description, :status, :createdAt, :updatedAt, :version
                )
                """, parameters(series));
    }

    private void updateSeries(Series series, long expectedVersion) {
        if (series.version().value() <= expectedVersion) {
            throw new OptimisticLockingFailureException("Series save is stale for id " + series.id().value());
        }
        MapSqlParameterSource parameters = parameters(series)
                .addValue("expectedVersion", expectedVersion);
        int updated = jdbc().update("""
                UPDATE publishing.series
                SET slug = :slug,
                    title = :title,
                    description = :description,
                    status = :status,
                    updated_at = :updatedAt,
                    version = :version
                WHERE id = :id AND version = :expectedVersion
                """, parameters);
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Series save is stale for id " + series.id().value());
        }
    }

    private MapSqlParameterSource parameters(Series series) {
        return new MapSqlParameterSource()
                .addValue("id", series.id().value())
                .addValue("language", series.language().name())
                .addValue("slug", series.slug().value())
                .addValue("title", series.title().value())
                .addValue("description", series.description().map(SeriesDescription::value).orElse(null))
                .addValue("status", series.status().name())
                .addValue("createdAt", Timestamp.from(series.createdAt()))
                .addValue("updatedAt", Timestamp.from(series.updatedAt()))
                .addValue("version", series.version().value());
    }

    private void replaceEntries(Series series) {
        jdbc().update("""
                DELETE FROM publishing.series_entries
                WHERE series_id = :seriesId
                """, Map.of("seriesId", series.id().value()));
        MapSqlParameterSource[] batch = series.entries().stream()
                .map(entry -> new MapSqlParameterSource()
                        .addValue("id", entry.id().value())
                        .addValue("seriesId", series.id().value())
                        .addValue("contentItemId", entry.contentItemId().value())
                        .addValue("position", entry.position().value())
                        .addValue("addedAt", Timestamp.from(entry.addedAt())))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO publishing.series_entries (
                    id, series_id, content_item_id, position, added_at
                ) VALUES (:id, :seriesId, :contentItemId, :position, :addedAt)
                """, batch);
    }

    private Optional<Series> loadSeries(String whereClause, Map<String, Object> params) {
        String sql = "SELECT id, language, slug, title, description, status, created_at, updated_at, version "
                + "FROM publishing.series WHERE " + whereClause;
        List<SeriesRow> rows = jdbc().query(sql, params, (resultSet, rowNumber) -> new SeriesRow(
                resultSet.getObject("id", UUID.class),
                ContentLanguage.valueOf(resultSet.getString("language")),
                resultSet.getString("slug"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                SeriesStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version")));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        SeriesRow row = rows.getFirst();
        return Optional.of(Series.rehydrate(
                SeriesId.from(row.id()),
                row.language(),
                SeriesSlug.of(row.slug()),
                SeriesTitle.of(row.title()),
                SeriesDescription.optional(row.description()).orElse(null),
                row.status(),
                loadEntries(row.id()),
                row.createdAt(),
                row.updatedAt(),
                Version.of(row.version())));
    }

    private List<SeriesEntry> loadEntries(UUID seriesId) {
        return jdbc().query("""
                SELECT id, content_item_id, position, added_at
                FROM publishing.series_entries
                WHERE series_id = :seriesId
                ORDER BY position
                """, Map.of("seriesId", seriesId), (resultSet, rowNumber) -> new SeriesEntry(
                SeriesEntryId.from(resultSet.getObject("id", UUID.class)),
                ContentId.from(resultSet.getObject("content_item_id", UUID.class)),
                SeriesEntryPosition.of(resultSet.getInt("position")),
                resultSet.getTimestamp("added_at").toInstant()));
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new ContentPublishingPersistenceException("JDBC series repository is not available.");
        }
        return available;
    }

    private TransactionTemplate transactionTemplate() {
        TransactionTemplate available = transactions.getIfAvailable();
        if (available == null) {
            throw new ContentPublishingPersistenceException("JDBC transaction infrastructure is not available.");
        }
        return available;
    }

    private record SeriesRow(
            UUID id,
            ContentLanguage language,
            String slug,
            String title,
            String description,
            SeriesStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
