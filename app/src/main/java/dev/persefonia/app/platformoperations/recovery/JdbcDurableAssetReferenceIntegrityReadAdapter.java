package dev.persefonia.app.platformoperations.recovery;

import dev.persefonia.platformoperations.application.recovery.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcDurableAssetReferenceIntegrityReadAdapter
        implements DurableAssetReferenceIntegrityReadPort {
    private static final int ISSUE_LIMIT = 100;
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    public JdbcDurableAssetReferenceIntegrityReadAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public DurableAssetReferenceIntegritySummary verify() {
        NamedParameterJdbcTemplate available = jdbc.getObject();
        Counts counts = available.queryForObject("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE assets.id IS NULL) AS dangling
                FROM (
                    SELECT 'CONTENT_OG_IMAGE' AS reference_kind, id AS source_id, og_image_asset_id AS asset_id
                    FROM publishing.content_items WHERE og_image_asset_id IS NOT NULL
                    UNION ALL
                    SELECT 'CONTENT_REVISION_OG_IMAGE', id, og_image_asset_id
                    FROM publishing.content_revisions WHERE og_image_asset_id IS NOT NULL
                    UNION ALL
                    SELECT 'SITE_DEFAULT_OG_IMAGE', id, default_og_image_asset_id
                    FROM portfolio.site_presentation_settings WHERE default_og_image_asset_id IS NOT NULL
                    UNION ALL
                    SELECT 'PROJECT_COVER', id, cover_asset_id
                    FROM portfolio.projects WHERE cover_asset_id IS NOT NULL
                    UNION ALL
                    SELECT 'ACTIVE_CV_DOCUMENT', id, asset_id
                    FROM portfolio.active_cv_documents WHERE asset_id IS NOT NULL
                    UNION ALL
                    SELECT 'DISCOVERY_OG_IMAGE', id, og_image_asset_id
                    FROM discovery.discoverable_resources WHERE og_image_asset_id IS NOT NULL
                ) refs
                LEFT JOIN media.assets assets ON assets.id = refs.asset_id
                """, Map.of(), (resultSet, rowNumber) ->
                new Counts(resultSet.getLong("total"), resultSet.getLong("dangling")));
        if (counts == null) counts = new Counts(0, 0);
        List<DurableAssetReferenceIssue> issues = available.query("""
                SELECT reference_kind, source_id, asset_id
                FROM (
                    SELECT 'CONTENT_OG_IMAGE' AS reference_kind, id AS source_id, og_image_asset_id AS asset_id
                    FROM publishing.content_items WHERE og_image_asset_id IS NOT NULL
                    UNION ALL
                    SELECT 'CONTENT_REVISION_OG_IMAGE', id, og_image_asset_id
                    FROM publishing.content_revisions WHERE og_image_asset_id IS NOT NULL
                    UNION ALL
                    SELECT 'SITE_DEFAULT_OG_IMAGE', id, default_og_image_asset_id
                    FROM portfolio.site_presentation_settings WHERE default_og_image_asset_id IS NOT NULL
                    UNION ALL
                    SELECT 'PROJECT_COVER', id, cover_asset_id
                    FROM portfolio.projects WHERE cover_asset_id IS NOT NULL
                    UNION ALL
                    SELECT 'ACTIVE_CV_DOCUMENT', id, asset_id
                    FROM portfolio.active_cv_documents WHERE asset_id IS NOT NULL
                    UNION ALL
                    SELECT 'DISCOVERY_OG_IMAGE', id, og_image_asset_id
                    FROM discovery.discoverable_resources WHERE og_image_asset_id IS NOT NULL
                ) refs
                WHERE NOT EXISTS (SELECT 1 FROM media.assets assets WHERE assets.id = refs.asset_id)
                ORDER BY reference_kind, source_id, asset_id
                LIMIT :limit
                """, Map.of("limit", ISSUE_LIMIT), (resultSet, rowNumber) -> new DurableAssetReferenceIssue(
                DurableAssetReferenceKind.valueOf(resultSet.getString("reference_kind")),
                resultSet.getObject("source_id", UUID.class),
                resultSet.getObject("asset_id", UUID.class)));
        return new DurableAssetReferenceIntegritySummary(
                counts.total(), counts.dangling(), issues, counts.dangling() > issues.size());
    }

    private record Counts(long total, long dangling) { }
}
