package dev.persefonia.app.discovery.persistence;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.index.PublicHomepageContentEntry;
import dev.persefonia.discovery.application.index.PublicHomepageContentQueryService;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPublicHomepageContentQueryAdapter implements PublicHomepageContentQueryService {
    private static final String SQL = """
            SELECT title, summary, public_url, source_entity_id
            FROM discovery.discoverable_resources
            WHERE source_context = 'CONTENT_PUBLISHING'
              AND source_type = 'CONTENT_ITEM'
              AND route_purpose = 'DETAIL'
              AND language = :language
              AND resource_type IN (:resourceTypes)
              AND indexing_policy = 'INDEX'
              AND public_url IS NOT NULL
              AND published_at IS NOT NULL
            ORDER BY published_at DESC, source_updated_at DESC NULLS LAST, public_url ASC
            LIMIT :limit
            """;

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    JdbcPublicHomepageContentQueryAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PublicHomepageContentEntry> latestWriting(DiscoveryLanguage language, int limit) {
        return find(language, List.of(DiscoverableResourceType.ARTICLE, DiscoverableResourceType.NOTE), limit);
    }

    @Override
    public List<PublicHomepageContentEntry> latestResearch(DiscoveryLanguage language, int limit) {
        return find(language, List.of(DiscoverableResourceType.RESEARCH), limit);
    }

    private List<PublicHomepageContentEntry> find(DiscoveryLanguage language, List<DiscoverableResourceType> resourceTypes, int limit) {
        if (language == null || limit < 1 || limit > 3) {
            throw new IllegalArgumentException("Invalid homepage content query");
        }
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new DiscoveryPersistenceException("JDBC discovery infrastructure is not available");
        }
        return available.query(SQL, new MapSqlParameterSource()
                        .addValue("language", language.name())
                        .addValue("resourceTypes", resourceTypes.stream().map(Enum::name).toList())
                        .addValue("limit", limit),
                (row, number) -> new PublicHomepageContentEntry(
                        row.getString("title"), row.getString("summary"), row.getString("public_url"),
                        java.util.Optional.of(java.util.UUID.fromString(row.getString("source_entity_id")))));
    }
}
