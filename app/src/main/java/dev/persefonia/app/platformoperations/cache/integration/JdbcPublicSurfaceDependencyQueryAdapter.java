package dev.persefonia.app.platformoperations.cache.integration;

import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.discovery.SeriesPublicRouteFactory;
import dev.persefonia.contentpublishing.application.port.ContentPublicSurfaceDependencyQuery;
import dev.persefonia.contentpublishing.application.port.PublicTranslationMemberRouteQuery;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicSurfaceDependencies;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSlug;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.medialibrary.application.publicview.AssetPublicVariantRouteFactory;
import dev.persefonia.medialibrary.application.publicview.AssetPublicVariantRouteQuery;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.VariantName;
import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.profileportfolio.application.port.ProjectPublicSurfaceDependencyQuery;
import dev.persefonia.profileportfolio.application.publicview.ProjectPublicSurface;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import dev.persefonia.taxonomy.application.discovery.TagPublicRouteFactory;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.application.port.TagPublicRouteQuery;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

@Component
public final class JdbcPublicSurfaceDependencyQueryAdapter implements
        ContentPublicSurfaceDependencyQuery,
        PublicTranslationMemberRouteQuery,
        ProjectPublicSurfaceDependencyQuery,
        AssetPublicVariantRouteQuery,
        TagPublicRouteQuery {

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final TagPublicRouteFactory tagRoutes = new TagPublicRouteFactory();
    private final SeriesPublicRouteFactory seriesRoutes = new SeriesPublicRouteFactory();
    private final ContentPublicRouteFactory contentRoutes = new ContentPublicRouteFactory();
    private final ProjectPublicRouteFactory projectRoutes = new ProjectPublicRouteFactory();
    private final AssetPublicVariantRouteFactory variantRoutes = new AssetPublicVariantRouteFactory();

    public JdbcPublicSurfaceDependencyQueryAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ContentPublicSurfaceDependencies findFor(ContentId contentId, int limit) {
        var parameters = new MapSqlParameterSource("contentId", contentId.value()).addValue("limit", limit);
        List<PublicUrl> tags = jdbc().query("""
                SELECT content.language, tag.slug
                FROM publishing.content_items content
                JOIN publishing.content_item_tags assignment ON assignment.content_item_id = content.id
                JOIN taxonomy.tags tag ON tag.id = assignment.tag_id
                WHERE content.id = :contentId AND tag.status = 'ACTIVE'
                ORDER BY tag.slug
                LIMIT :limit
                """, parameters, (rs, row) -> tagRoutes.publicUrl(
                        DiscoveryLanguage.valueOf(rs.getString("language")), TagSlug.ofCanonical(rs.getString("slug"))));
        List<PublicUrl> series = jdbc().query("""
                SELECT series.language, series.slug
                FROM publishing.series_entries entry
                JOIN publishing.series series ON series.id = entry.series_id
                WHERE entry.content_item_id = :contentId AND series.status = 'ACTIVE'
                ORDER BY series.language, series.slug
                LIMIT :limit
                """, parameters, (rs, row) -> seriesRoutes.publicUrl(
                        ContentLanguage.valueOf(rs.getString("language")), SeriesSlug.of(rs.getString("slug"))));
        List<PublicUrl> translations = translationRoutesForContent(contentId, limit);
        boolean overflow = tags.size() == limit || series.size() == limit || translations.size() == limit;
        return new ContentPublicSurfaceDependencies(tags, series, translations, overflow);
    }

    @Override
    public List<PublicUrl> findPublicMemberRoutes(TranslationGroupId groupId, int limit) {
        return jdbc().query("""
                SELECT content.type, content.language, content.slug
                FROM publishing.translation_group_entries entry
                JOIN publishing.content_items content ON content.id = entry.content_item_id
                WHERE entry.translation_group_id = :groupId
                  AND content.status = 'PUBLISHED'
                  AND content.visibility = 'PUBLIC'
                ORDER BY content.language, content.slug
                LIMIT :limit
                """, new MapSqlParameterSource("groupId", groupId.value()).addValue("limit", limit),
                (rs, row) -> contentRoutes.publicUrl(
                        ContentType.valueOf(rs.getString("type")),
                        ContentLanguage.valueOf(rs.getString("language")),
                        Slug.of(rs.getString("slug"))));
    }

    private List<PublicUrl> translationRoutesForContent(ContentId contentId, int limit) {
        return jdbc().query("""
                SELECT member.type, member.language, member.slug
                FROM publishing.translation_group_entries owner
                JOIN publishing.translation_group_entries sibling
                  ON sibling.translation_group_id = owner.translation_group_id
                JOIN publishing.content_items member ON member.id = sibling.content_item_id
                WHERE owner.content_item_id = :contentId
                  AND member.status = 'PUBLISHED'
                  AND member.visibility = 'PUBLIC'
                ORDER BY member.language, member.slug
                LIMIT :limit
                """, new MapSqlParameterSource("contentId", contentId.value()).addValue("limit", limit),
                (rs, row) -> contentRoutes.publicUrl(
                        ContentType.valueOf(rs.getString("type")),
                        ContentLanguage.valueOf(rs.getString("language")),
                        Slug.of(rs.getString("slug"))));
    }

    @Override
    public List<ProjectPublicSurface> findReferencing(TagId tagId, int limit) {
        List<ProjectRow> rows = jdbc().query("""
                WITH affected AS (
                    SELECT project.id, project.status, project.visibility, project.featured
                    FROM portfolio.project_tags assignment
                    JOIN portfolio.projects project ON project.id = assignment.project_id
                    WHERE assignment.tag_id = :tagId
                    ORDER BY project.id
                    LIMIT :limit
                )
                SELECT affected.id, affected.status, affected.visibility, affected.featured,
                       localization.language, localization.slug
                FROM affected
                JOIN portfolio.project_localizations localization ON localization.project_id = affected.id
                ORDER BY affected.id, localization.language
                """, new MapSqlParameterSource("tagId", tagId.value()).addValue("limit", limit),
                (rs, row) -> new ProjectRow(
                        rs.getObject("id", UUID.class), rs.getString("status"), rs.getString("visibility"),
                        rs.getBoolean("featured"),
                        dev.persefonia.profileportfolio.domain.common.ContentLanguage.valueOf(rs.getString("language")),
                        ProjectSlug.of(rs.getString("slug"))));
        Map<UUID, MutableProjectSurface> grouped = new LinkedHashMap<>();
        for (ProjectRow row : rows) {
            grouped.computeIfAbsent(row.id(), ignored -> new MutableProjectSurface(row.status(), row.visibility(), row.featured()))
                    .routes.put(row.language(), projectRoutes.publicUrl(row.language(), row.slug()));
        }
        return grouped.values().stream().map(MutableProjectSurface::toPublicSurface).toList();
    }

    @Override
    public List<String> findStableVariantRoutes(AssetId assetId, int limit) {
        return jdbc().query("""
                SELECT name
                FROM media.asset_variants
                WHERE asset_id = :assetId
                ORDER BY name
                LIMIT :limit
                """, new MapSqlParameterSource("assetId", assetId.value()).addValue("limit", limit),
                (rs, row) -> variantRoutes.route(assetId, VariantName.fromDatabaseValue(rs.getString("name"))));
    }

    @Override
    public List<PublicUrl> findActiveRoutes(
            Set<dev.persefonia.taxonomy.domain.model.TagId> tagIds,
            DiscoveryLanguage language,
            int limit) {
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return jdbc().query("""
                SELECT slug
                FROM taxonomy.tags
                WHERE id IN (:tagIds) AND status = 'ACTIVE'
                ORDER BY slug
                LIMIT :limit
                """, new MapSqlParameterSource("tagIds", tagIds.stream().map(
                        dev.persefonia.taxonomy.domain.model.TagId::value).toList()).addValue("limit", limit),
                (rs, row) -> tagRoutes.publicUrl(language, TagSlug.ofCanonical(rs.getString("slug"))));
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new IllegalStateException("public-surface dependency JDBC infrastructure is unavailable");
        }
        return available;
    }

    private record ProjectRow(
            UUID id, String status, String visibility, boolean featured,
            dev.persefonia.profileportfolio.domain.common.ContentLanguage language, ProjectSlug slug) {
    }

    private static final class MutableProjectSurface {
        private final String status;
        private final String visibility;
        private final boolean featured;
        private final Map<dev.persefonia.profileportfolio.domain.common.ContentLanguage, PublicUrl> routes =
                new EnumMap<>(dev.persefonia.profileportfolio.domain.common.ContentLanguage.class);

        private MutableProjectSurface(String status, String visibility, boolean featured) {
            this.status = status;
            this.visibility = visibility;
            this.featured = featured;
        }

        private ProjectPublicSurface toPublicSurface() {
            boolean direct = visibility.equals("PUBLIC") || visibility.equals("UNLISTED");
            boolean listed = visibility.equals("PUBLIC") && !status.equals("ARCHIVED");
            return new ProjectPublicSurface(direct ? routes : Map.of(), listed, listed && featured);
        }
    }
}
