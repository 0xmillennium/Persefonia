package dev.persefonia.app.discovery.persistence;

import dev.persefonia.discovery.application.contract.CanonicalUrl;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.domain.DiscoverableResource;
import dev.persefonia.discovery.domain.DiscoverableResourceId;
import dev.persefonia.discovery.domain.DiscoverableResourceKey;
import dev.persefonia.discovery.domain.OpenGraphDescription;
import dev.persefonia.discovery.domain.OpenGraphTitle;
import dev.persefonia.discovery.domain.ResourceSummary;
import dev.persefonia.discovery.domain.ResourceTitle;
import dev.persefonia.discovery.domain.SearchText;
import dev.persefonia.discovery.domain.SocialPreviewProfile;
import dev.persefonia.discovery.domain.Version;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

final class DiscoverableResourceMapper {
    DiscoverableResource fromRow(ResultSet row, int rowNumber) throws SQLException {
        return DiscoverableResource.createCurrent(
                new DiscoverableResourceId(row.getObject("id", java.util.UUID.class)),
                new DiscoverableResourceKey(
                        enumValue(SourceContext.class, row.getString("source_context")),
                        enumValue(SourceType.class, row.getString("source_type")),
                        new SourceEntityId(row.getObject("source_entity_id", java.util.UUID.class)),
                        enumValue(DiscoverableResourceType.class, row.getString("resource_type")),
                        enumValue(DiscoveryLanguage.class, row.getString("language")),
                        enumValue(RoutePurpose.class, row.getString("route_purpose"))),
                new PublicUrl(row.getString("public_url")),
                new CanonicalUrl(row.getString("canonical_url")),
                new ResourceTitle(row.getString("title")),
                new ResourceSummary(row.getString("summary")),
                enumValue(IndexingPolicy.class, row.getString("indexing_policy")),
                enumValue(DiscoveryEligibility.class, row.getString("search_eligibility")),
                enumValue(DiscoveryEligibility.class, row.getString("sitemap_eligibility")),
                enumValue(DiscoveryEligibility.class, row.getString("feed_eligibility")),
                new SocialPreviewProfile(
                        nullable(row.getString("og_title"), OpenGraphTitle::new),
                        nullable(row.getString("og_description"), OpenGraphDescription::new),
                        row.getObject("og_image_asset_id", java.util.UUID.class)),
                instant(row, "published_at"),
                instant(row, "source_updated_at"),
                new SearchText(row.getString("search_text")),
                row.getTimestamp("created_at").toInstant(),
                Version.of(row.getLong("version")));
    }

    private Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private <T> T nullable(String value, java.util.function.Function<String, T> mapper) {
        return value == null ? null : mapper.apply(value);
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null) {
            throw new DiscoveryPersistenceException("Persisted " + type.getSimpleName() + " must not be null");
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new DiscoveryPersistenceException("Unknown persisted " + type.getSimpleName() + ": " + value, exception);
        }
    }
}
