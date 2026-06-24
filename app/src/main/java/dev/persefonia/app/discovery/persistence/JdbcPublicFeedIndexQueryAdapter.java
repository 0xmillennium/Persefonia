package dev.persefonia.app.discovery.persistence;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.index.PublicFeedEntry;
import dev.persefonia.discovery.application.index.PublicFeedIndexQueryService;
import dev.persefonia.discovery.application.index.PublicIndexLimits;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPublicFeedIndexQueryAdapter implements PublicFeedIndexQueryService {
    private static final String FEED_SQL = """
            SELECT
              source_type,
              source_entity_id::text AS source_id,
              language,
              public_url,
              canonical_url,
              title,
              summary,
              published_at,
              coalesce(source_updated_at, published_at) AS updated_at
            FROM discovery.discoverable_resources
            WHERE indexing_policy = 'INDEX'
              AND feed_eligibility = 'ELIGIBLE'
              AND public_url IS NOT NULL
              AND canonical_url IS NOT NULL
              AND published_at IS NOT NULL
            ORDER BY
              published_at DESC,
              source_updated_at DESC NULLS LAST,
              public_url ASC
            LIMIT :limit
            """;

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    JdbcPublicFeedIndexQueryAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PublicFeedEntry> findLatestFeedEntries(int limit) {
        int boundedLimit = PublicIndexLimits.requireFeedLimit(limit);
        return jdbc().query(
                FEED_SQL,
                new MapSqlParameterSource("limit", boundedLimit),
                this::feedEntry);
    }

    private PublicFeedEntry feedEntry(ResultSet row, int rowNumber) throws SQLException {
        return new PublicFeedEntry(
                row.getString("source_type"),
                row.getString("source_id"),
                DiscoveryLanguage.valueOf(row.getString("language")),
                row.getString("public_url"),
                row.getString("canonical_url"),
                row.getString("title"),
                row.getString("summary"),
                instant(row, "published_at"),
                instant(row, "updated_at"));
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
