package dev.persefonia.app.discovery.persistence;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.domain.DiscoverableResource;
import dev.persefonia.discovery.domain.DiscoverableResourceId;
import dev.persefonia.discovery.domain.DiscoverableResourceKey;
import dev.persefonia.discovery.domain.DiscoverableResourceRepository;
import dev.persefonia.discovery.domain.SourceEntityRef;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresDiscoverableResourceRepository implements DiscoverableResourceRepository {
    private static final String COLUMNS = """
            id, source_context, source_type, source_entity_id, resource_type, route_purpose, language,
            public_url, canonical_url, title, summary, indexing_policy, search_eligibility,
            sitemap_eligibility, feed_eligibility, og_title, og_description, og_image_asset_id,
            published_at, source_updated_at, search_text, created_at, version
            """;

    private static final String INSERT = """
            INSERT INTO discovery.discoverable_resources (
                id, source_context, source_type, source_entity_id, resource_type, route_purpose, language,
                public_url, canonical_url, title, summary, indexing_policy, search_eligibility,
                sitemap_eligibility, feed_eligibility, og_title, og_description, og_image_asset_id,
                published_at, source_updated_at, search_text, created_at, version
            ) VALUES (
                :id, :sourceContext, :sourceType, :sourceEntityId, :resourceType, :routePurpose, :language,
                :publicUrl, :canonicalUrl, :title, :summary, :indexingPolicy, :searchEligibility,
                :sitemapEligibility, :feedEligibility, :ogTitle, :ogDescription, :ogImageAssetId,
                :publishedAt, :sourceUpdatedAt, :searchText, :createdAt, :version
            )
            RETURNING
            """ + COLUMNS;

    private static final String REPLACE_BY_KEY = """
            INSERT INTO discovery.discoverable_resources (
                id, source_context, source_type, source_entity_id, resource_type, route_purpose, language,
                public_url, canonical_url, title, summary, indexing_policy, search_eligibility,
                sitemap_eligibility, feed_eligibility, og_title, og_description, og_image_asset_id,
                published_at, source_updated_at, search_text, created_at, version
            ) VALUES (
                :id, :sourceContext, :sourceType, :sourceEntityId, :resourceType, :routePurpose, :language,
                :publicUrl, :canonicalUrl, :title, :summary, :indexingPolicy, :searchEligibility,
                :sitemapEligibility, :feedEligibility, :ogTitle, :ogDescription, :ogImageAssetId,
                :publishedAt, :sourceUpdatedAt, :searchText, :createdAt, :version
            )
            ON CONFLICT ON CONSTRAINT uq_discoverable_resources_key
            DO UPDATE SET
                public_url = excluded.public_url,
                canonical_url = excluded.canonical_url,
                title = excluded.title,
                summary = excluded.summary,
                indexing_policy = excluded.indexing_policy,
                search_eligibility = excluded.search_eligibility,
                sitemap_eligibility = excluded.sitemap_eligibility,
                feed_eligibility = excluded.feed_eligibility,
                og_title = excluded.og_title,
                og_description = excluded.og_description,
                og_image_asset_id = excluded.og_image_asset_id,
                published_at = excluded.published_at,
                source_updated_at = excluded.source_updated_at,
                search_text = excluded.search_text,
                version = discovery.discoverable_resources.version + 1
            RETURNING
            """ + COLUMNS;

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final DiscoverableResourceMapper mapper = new DiscoverableResourceMapper();

    PostgresDiscoverableResourceRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DiscoverableResource save(DiscoverableResource resource) {
        Objects.requireNonNull(resource, "resource");
        return one(INSERT, parameters(resource));
    }

    @Override
    public DiscoverableResource replaceByKey(DiscoverableResource resource) {
        Objects.requireNonNull(resource, "resource");
        return one(REPLACE_BY_KEY, parameters(resource));
    }

    @Override
    public Optional<DiscoverableResource> findById(DiscoverableResourceId id) {
        Objects.requireNonNull(id, "id");
        return optional("SELECT " + COLUMNS + """
                FROM discovery.discoverable_resources
                WHERE id = :id
                """, new MapSqlParameterSource("id", id.value()));
    }

    @Override
    public Optional<DiscoverableResource> findByKey(DiscoverableResourceKey key) {
        Objects.requireNonNull(key, "key");
        return optional("SELECT " + COLUMNS + """
                FROM discovery.discoverable_resources
                WHERE source_context = :sourceContext
                  AND source_type = :sourceType
                  AND source_entity_id = :sourceEntityId
                  AND resource_type = :resourceType
                  AND language = :language
                  AND route_purpose = :routePurpose
                """, keyParameters(key));
    }

    @Override
    public Optional<DiscoverableResource> findByPublicUrl(PublicUrl publicUrl) {
        Objects.requireNonNull(publicUrl, "publicUrl");
        return optional("SELECT " + COLUMNS + """
                FROM discovery.discoverable_resources
                WHERE public_url = :publicUrl
                """, new MapSqlParameterSource("publicUrl", publicUrl.value()));
    }

    @Override
    public List<DiscoverableResource> findBySourceRef(SourceEntityRef sourceRef) {
        Objects.requireNonNull(sourceRef, "sourceRef");
        return query("SELECT " + COLUMNS + """
                FROM discovery.discoverable_resources
                WHERE source_context = :sourceContext
                  AND source_type = :sourceType
                  AND source_entity_id = :sourceEntityId
                ORDER BY created_at, id
                """, sourceRefParameters(sourceRef));
    }

    @Override
    public int removeBySourceRef(SourceEntityRef sourceRef) {
        Objects.requireNonNull(sourceRef, "sourceRef");
        return jdbc().update("""
                DELETE FROM discovery.discoverable_resources
                WHERE source_context = :sourceContext
                  AND source_type = :sourceType
                  AND source_entity_id = :sourceEntityId
                """, sourceRefParameters(sourceRef));
    }

    private MapSqlParameterSource parameters(DiscoverableResource resource) {
        return keyParameters(resource.key())
                .addValue("id", resource.id().value())
                .addValue("publicUrl", resource.publicUrl().value())
                .addValue("canonicalUrl", resource.canonicalUrl().value())
                .addValue("title", resource.title().value())
                .addValue("summary", resource.summary().value())
                .addValue("indexingPolicy", resource.indexingPolicy().name())
                .addValue("searchEligibility", resource.searchEligibility().name())
                .addValue("sitemapEligibility", resource.sitemapEligibility().name())
                .addValue("feedEligibility", resource.feedEligibility().name())
                .addValue("ogTitle", resource.openGraph().title() == null ? null : resource.openGraph().title().value())
                .addValue("ogDescription", resource.openGraph().description() == null
                        ? null : resource.openGraph().description().value())
                .addValue("ogImageAssetId", resource.openGraph().imageAssetId())
                .addValue("publishedAt", resource.publishedAt().map(Timestamp::from).orElse(null))
                .addValue("sourceUpdatedAt", resource.sourceUpdatedAt().map(Timestamp::from).orElse(null))
                .addValue("searchText", resource.searchText().value())
                .addValue("createdAt", Timestamp.from(resource.createdAt()))
                .addValue("version", resource.version().value());
    }

    private MapSqlParameterSource keyParameters(DiscoverableResourceKey key) {
        return sourceRefParameters(key.sourceRef())
                .addValue("resourceType", key.resourceType().name())
                .addValue("language", key.language().name())
                .addValue("routePurpose", key.routePurpose().name());
    }

    private MapSqlParameterSource sourceRefParameters(SourceEntityRef sourceRef) {
        return new MapSqlParameterSource()
                .addValue("sourceContext", sourceRef.sourceContext().name())
                .addValue("sourceType", sourceRef.sourceType().name())
                .addValue("sourceEntityId", sourceRef.sourceEntityId().value());
    }

    private DiscoverableResource one(String sql, MapSqlParameterSource parameters) {
        return query(sql, parameters).stream()
                .findFirst()
                .orElseThrow(() -> new DiscoveryPersistenceException("Expected a discoverable resource row"));
    }

    private Optional<DiscoverableResource> optional(String sql, MapSqlParameterSource parameters) {
        return query(sql, parameters).stream().findFirst();
    }

    private List<DiscoverableResource> query(String sql, MapSqlParameterSource parameters) {
        return jdbc().query(sql, parameters, mapper::fromRow);
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new DiscoveryPersistenceException("JDBC discovery infrastructure is not available");
        }
        return available;
    }
}
