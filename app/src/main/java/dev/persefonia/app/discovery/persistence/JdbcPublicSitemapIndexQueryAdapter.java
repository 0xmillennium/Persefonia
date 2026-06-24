package dev.persefonia.app.discovery.persistence;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.index.PublicIndexLimits;
import dev.persefonia.discovery.application.index.PublicSitemapEntry;
import dev.persefonia.discovery.application.index.PublicSitemapIndexQueryService;
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
public class JdbcPublicSitemapIndexQueryAdapter implements PublicSitemapIndexQueryService {
    private static final String SITEMAP_SQL = """
            SELECT
              public_url,
              canonical_url,
              language,
              coalesce(source_updated_at, published_at) AS last_modified_at
            FROM discovery.discoverable_resources
            WHERE indexing_policy = 'INDEX'
              AND sitemap_eligibility = 'ELIGIBLE'
              AND public_url IS NOT NULL
              AND canonical_url IS NOT NULL
            ORDER BY
              language ASC,
              public_url ASC
            LIMIT :limit
            """;

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    JdbcPublicSitemapIndexQueryAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PublicSitemapEntry> findSitemapEntries(int limit) {
        int boundedLimit = PublicIndexLimits.requireSitemapLimit(limit);
        return jdbc().query(
                SITEMAP_SQL,
                new MapSqlParameterSource("limit", boundedLimit),
                this::sitemapEntry);
    }

    private PublicSitemapEntry sitemapEntry(ResultSet row, int rowNumber) throws SQLException {
        return new PublicSitemapEntry(
                row.getString("public_url"),
                row.getString("canonical_url"),
                DiscoveryLanguage.valueOf(row.getString("language")),
                instant(row, "last_modified_at"));
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
