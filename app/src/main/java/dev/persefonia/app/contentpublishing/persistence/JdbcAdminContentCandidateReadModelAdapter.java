package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.application.port.SeriesCandidateContentReadModel;
import dev.persefonia.contentpublishing.application.port.TranslationCandidateContentReadModel;
import dev.persefonia.contentpublishing.application.query.SeriesCandidateContentItem;
import dev.persefonia.contentpublishing.application.query.TranslationCandidateItem;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminContentCandidateReadModelAdapter implements
        SeriesCandidateContentReadModel,
        TranslationCandidateContentReadModel {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    JdbcAdminContentCandidateReadModelAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<SeriesCandidateContentItem> candidatesFor(SeriesId seriesId, ContentLanguage language) {
        Objects.requireNonNull(seriesId, "seriesId");
        Objects.requireNonNull(language, "language");
        return jdbc().query("""
                SELECT content_items.id,
                       content_items.type,
                       content_items.status,
                       content_items.title
                FROM publishing.content_items content_items
                WHERE content_items.language = :language
                  AND content_items.status IN ('DRAFT', 'UNPUBLISHED', 'PUBLISHED')
                  AND NOT EXISTS (
                      SELECT 1
                      FROM publishing.series_entries series_entries
                      WHERE series_entries.series_id = :seriesId
                        AND series_entries.content_item_id = content_items.id
                  )
                ORDER BY content_items.updated_at DESC, content_items.id ASC
                """, Map.of(
                "seriesId", seriesId.value(),
                "language", language.name()), (resultSet, rowNumber) -> new SeriesCandidateContentItem(
                ContentId.from(resultSet.getObject("id", UUID.class)),
                ContentType.valueOf(resultSet.getString("type")),
                ContentStatus.valueOf(resultSet.getString("status")),
                Optional.ofNullable(resultSet.getString("title"))));
    }

    @Override
    public List<TranslationCandidateItem> candidatesFor(TranslationGroupId groupId, ContentType contentType) {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(contentType, "contentType");
        return jdbc().query("""
                SELECT content_items.id,
                       content_items.language,
                       content_items.title
                FROM publishing.content_items content_items
                WHERE content_items.type = :contentType
                  AND content_items.status IN ('DRAFT', 'UNPUBLISHED', 'PUBLISHED')
                  AND NOT EXISTS (
                      SELECT 1
                      FROM publishing.translation_group_entries grouped_content
                      WHERE grouped_content.content_item_id = content_items.id
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM publishing.translation_group_entries group_languages
                      WHERE group_languages.translation_group_id = :groupId
                        AND group_languages.language = content_items.language
                  )
                ORDER BY content_items.updated_at DESC, content_items.id ASC
                """, Map.of(
                "groupId", groupId.value(),
                "contentType", contentType.name()), (resultSet, rowNumber) -> new TranslationCandidateItem(
                ContentId.from(resultSet.getObject("id", UUID.class)),
                ContentLanguage.valueOf(resultSet.getString("language")),
                Optional.ofNullable(resultSet.getString("title"))));
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new ContentPublishingPersistenceException("JDBC admin content candidate read model is not available.");
        }
        return available;
    }
}
