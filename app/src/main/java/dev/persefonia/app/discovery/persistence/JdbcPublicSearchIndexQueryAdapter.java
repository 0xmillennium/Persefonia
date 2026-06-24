package dev.persefonia.app.discovery.persistence;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.index.PublicSearchIndexQueryService;
import dev.persefonia.discovery.application.index.PublicSearchRequest;
import dev.persefonia.discovery.application.index.PublicSearchResult;
import dev.persefonia.discovery.application.index.PublicSearchResultPage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPublicSearchIndexQueryAdapter implements PublicSearchIndexQueryService {
    private static final String MATCHING_RESOURCES = """
            FROM discovery.discoverable_resources, q
            WHERE indexing_policy = 'INDEX'
              AND search_eligibility = 'ELIGIBLE'
              AND public_url IS NOT NULL
              AND canonical_url IS NOT NULL
              AND q.query @@ to_tsvector('simple', coalesce(search_text, ''))
            """;

    private static final String SEARCH_SQL = """
            WITH q AS (
              SELECT websearch_to_tsquery('simple', :query) AS query
            )
            SELECT
              source_type,
              source_entity_id::text AS source_id,
              language,
              public_url,
              canonical_url,
              title,
              summary,
              published_at,
              source_updated_at,
              ts_rank_cd(to_tsvector('simple', coalesce(search_text, '')), q.query) AS rank
            """ + MATCHING_RESOURCES + """
            ORDER BY
              rank DESC,
              published_at DESC NULLS LAST,
              source_updated_at DESC NULLS LAST,
              public_url ASC
            LIMIT :limit
            OFFSET :offset
            """;

    private static final String COUNT_SQL = """
            WITH q AS (
              SELECT websearch_to_tsquery('simple', :query) AS query
            )
            SELECT count(*)
            """ + MATCHING_RESOURCES;

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    JdbcPublicSearchIndexQueryAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PublicSearchResultPage search(PublicSearchRequest request) {
        Objects.requireNonNull(request, "request");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("query", request.query())
                .addValue("limit", request.limit())
                .addValue("offset", request.offset());

        List<PublicSearchResult> results = jdbc().query(SEARCH_SQL, parameters, this::searchResult);
        Long count = jdbc().queryForObject(COUNT_SQL, parameters, Long.class);
        return new PublicSearchResultPage(
                request.query(),
                request.limit(),
                request.offset(),
                count == null ? 0L : count,
                results);
    }

    private PublicSearchResult searchResult(ResultSet row, int rowNumber) throws SQLException {
        return new PublicSearchResult(
                row.getString("source_type"),
                row.getString("source_id"),
                DiscoveryLanguage.valueOf(row.getString("language")),
                row.getString("public_url"),
                row.getString("canonical_url"),
                row.getString("title"),
                row.getString("summary"),
                instant(row, "published_at"),
                instant(row, "source_updated_at"),
                row.getDouble("rank"));
    }

    private Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp timestamp = row.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new DiscoveryPersistenceException("JDBC discovery infrastructure is not available");
        }
        return available;
    }
}
