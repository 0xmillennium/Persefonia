package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.application.discovery.ConfiguredContentCanonicalUrlFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.port.PublicSeriesEntryReadModel;
import dev.persefonia.contentpublishing.application.port.PublicTaggedContentReadModel;
import dev.persefonia.contentpublishing.application.port.PublicTranslationReadModel;
import dev.persefonia.contentpublishing.application.query.PublicContentPageResult;
import dev.persefonia.contentpublishing.application.query.PublicHreflangLink;
import dev.persefonia.contentpublishing.application.query.PublicSeriesEntryItem;
import dev.persefonia.contentpublishing.application.query.PublicTaggedContentItem;
import dev.persefonia.contentpublishing.application.query.PublicTaggedContentQuery;
import dev.persefonia.contentpublishing.application.query.PublicTranslationLink;
import dev.persefonia.contentpublishing.application.query.PublicTranslationLinkSet;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.discovery.application.contract.PublicUrl;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPublicContentReadModelAdapter implements
        PublicTaggedContentReadModel,
        PublicSeriesEntryReadModel,
        PublicTranslationReadModel {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final ContentPublicRouteFactory routeFactory;
    private final ConfiguredContentCanonicalUrlFactory canonicalUrlFactory;

    JdbcPublicContentReadModelAdapter(
            ObjectProvider<NamedParameterJdbcTemplate> jdbc,
            ContentPublicRouteFactory routeFactory,
            ConfiguredContentCanonicalUrlFactory canonicalUrlFactory) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory");
    }

    @Override
    public List<PublicTaggedContentItem> list(PublicTaggedContentQuery query) {
        Objects.requireNonNull(query, "query");
        return jdbc().query("""
                SELECT content_items.type,
                       content_items.language,
                       content_items.slug,
                       content_items.title,
                       content_items.summary,
                       content_items.canonical_path,
                       content_items.published_at
                FROM publishing.content_item_tags tags
                JOIN publishing.content_items content_items
                  ON content_items.id = tags.content_item_id
                JOIN publishing.content_render_snapshots snapshots
                  ON snapshots.content_item_id = content_items.id
                WHERE tags.tag_id = :tagId
                  AND content_items.status = 'PUBLISHED'
                  AND content_items.visibility = 'PUBLIC'
                  AND content_items.language = :language
                  AND content_items.slug IS NOT NULL
                  AND content_items.title IS NOT NULL
                  AND content_items.summary IS NOT NULL
                  AND content_items.canonical_path IS NOT NULL
                  AND content_items.published_at IS NOT NULL
                ORDER BY content_items.published_at DESC, content_items.id ASC
                LIMIT :limit
                """, Map.of(
                "tagId", query.tagId().value(),
                "language", query.language().name(),
                "limit", query.limit()), (resultSet, rowNumber) -> contentRow(resultSet)).stream()
                .filter(row -> row.hasCurrentCanonicalPath())
                .map(row -> new PublicTaggedContentItem(
                        row.title(),
                        row.summary(),
                        row.publicUrl(),
                        row.publicUrl(),
                        row.type().name(),
                        row.publishedAt(),
                        row.language().name()))
                .toList();
    }

    @Override
    public List<PublicSeriesEntryItem> listEntries(SeriesId seriesId, ContentLanguage language) {
        Objects.requireNonNull(seriesId, "seriesId");
        Objects.requireNonNull(language, "language");
        return jdbc().query("""
                SELECT series_entries.position,
                       content_items.type,
                       content_items.language,
                       content_items.slug,
                       content_items.title,
                       content_items.summary,
                       content_items.canonical_path,
                       content_items.published_at
                FROM publishing.series_entries series_entries
                JOIN publishing.content_items content_items
                  ON content_items.id = series_entries.content_item_id
                JOIN publishing.content_render_snapshots snapshots
                  ON snapshots.content_item_id = content_items.id
                WHERE series_entries.series_id = :seriesId
                  AND content_items.status = 'PUBLISHED'
                  AND content_items.visibility = 'PUBLIC'
                  AND content_items.language = :language
                  AND content_items.slug IS NOT NULL
                  AND content_items.title IS NOT NULL
                  AND content_items.summary IS NOT NULL
                  AND content_items.canonical_path IS NOT NULL
                  AND content_items.published_at IS NOT NULL
                ORDER BY series_entries.position ASC
                """, Map.of(
                "seriesId", seriesId.value(),
                "language", language.name()), (resultSet, rowNumber) -> new SeriesContentRow(
                resultSet.getInt("position"),
                contentRow(resultSet))).stream()
                .filter(row -> row.content().hasCurrentCanonicalPath())
                .map(row -> new PublicSeriesEntryItem(
                        row.position(),
                        row.content().title(),
                        row.content().summary(),
                        row.content().publicUrl(),
                        row.content().publicUrl(),
                        row.content().type().name(),
                        row.content().publishedAt(),
                        row.content().language().name()))
                .toList();
    }

    @Override
    public PublicTranslationLinkSet linksFor(PublicContentPageResult currentPage) {
        Objects.requireNonNull(currentPage, "currentPage");
        List<UUID> groupIds = jdbc().query("""
                SELECT translation_group_id
                FROM publishing.translation_group_entries
                WHERE content_item_id = :contentItemId
                """, Map.of("contentItemId", currentPage.contentId().value()),
                (resultSet, rowNumber) -> resultSet.getObject("translation_group_id", UUID.class));
        if (groupIds.isEmpty()) {
            return PublicTranslationLinkSet.empty();
        }

        List<PublicTranslationLink> visibleLinks = jdbc().query("""
                SELECT content_items.type,
                       content_items.language,
                       content_items.slug,
                       content_items.title,
                       content_items.summary,
                       content_items.canonical_path,
                       content_items.published_at
                FROM publishing.translation_group_entries entries
                JOIN publishing.content_items content_items
                  ON content_items.id = entries.content_item_id
                JOIN publishing.content_render_snapshots snapshots
                  ON snapshots.content_item_id = content_items.id
                WHERE entries.translation_group_id = :groupId
                  AND entries.content_item_id <> :contentItemId
                  AND content_items.status = 'PUBLISHED'
                  AND content_items.visibility = 'PUBLIC'
                  AND content_items.slug IS NOT NULL
                  AND content_items.title IS NOT NULL
                  AND content_items.canonical_path IS NOT NULL
                ORDER BY entries.added_at ASC, entries.id ASC
                """, Map.of(
                "groupId", groupIds.getFirst(),
                "contentItemId", currentPage.contentId().value()), (resultSet, rowNumber) -> contentRow(resultSet)).stream()
                .filter(row -> row.hasCurrentCanonicalPath())
                .map(row -> new PublicTranslationLink(
                        languageCode(row.language()),
                        languageLabel(row.language()),
                        row.title(),
                        row.publicUrl(),
                        canonicalUrl(row.publicUrl())))
                .toList();

        if (visibleLinks.isEmpty()) {
            return PublicTranslationLinkSet.empty();
        }

        List<PublicHreflangLink> hreflangLinks = new ArrayList<>();
        hreflangLinks.add(new PublicHreflangLink(
                languageCode(currentPage.language()),
                canonicalUrl(currentPage.canonicalPath().value())));
        visibleLinks.stream()
                .map(link -> new PublicHreflangLink(link.language(), link.canonicalUrl()))
                .forEach(hreflangLinks::add);
        return PublicTranslationLinkSet.withAlternates(visibleLinks, hreflangLinks);
    }

    private ContentRow contentRow(ResultSet resultSet) throws SQLException {
        ContentType type = ContentType.valueOf(resultSet.getString("type"));
        ContentLanguage language = ContentLanguage.valueOf(resultSet.getString("language"));
        String publicUrl = routeFactory.publicUrl(type, language, Slug.of(resultSet.getString("slug"))).value();
        return new ContentRow(
                type,
                language,
                resultSet.getString("title"),
                resultSet.getString("summary"),
                publicUrl,
                resultSet.getString("canonical_path"),
                resultSet.getTimestamp("published_at").toInstant());
    }

    private String canonicalUrl(String publicUrl) {
        return canonicalUrlFactory.canonicalUrl(new PublicUrl(publicUrl)).value();
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new ContentPublishingPersistenceException("JDBC public content read model is not available.");
        }
        return available;
    }

    private static String languageCode(ContentLanguage language) {
        return language.name().toLowerCase(Locale.ROOT);
    }

    private static String languageLabel(ContentLanguage language) {
        return switch (language) {
            case EN -> "English";
            case TR -> "Turkish";
        };
    }

    private record ContentRow(
            ContentType type,
            ContentLanguage language,
            String title,
            String summary,
            String publicUrl,
            String canonicalPath,
            Instant publishedAt) {
        private boolean hasCurrentCanonicalPath() {
            return publicUrl.equals(canonicalPath);
        }
    }

    private record SeriesContentRow(int position, ContentRow content) {
    }
}
