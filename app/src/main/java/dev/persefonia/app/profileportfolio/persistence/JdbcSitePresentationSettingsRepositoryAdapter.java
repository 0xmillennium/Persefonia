package dev.persefonia.app.profileportfolio.persistence;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.settings.HomepageSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsId;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import java.sql.Timestamp;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcSitePresentationSettingsRepositoryAdapter implements SitePresentationSettingsRepository {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final ObjectProvider<TransactionTemplate> transactions;
    private final SitePresentationSettingsPersistenceMapper mapper = new SitePresentationSettingsPersistenceMapper();

    JdbcSitePresentationSettingsRepositoryAdapter(
            ObjectProvider<NamedParameterJdbcTemplate> jdbc,
            ObjectProvider<TransactionTemplate> transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public SitePresentationSettings save(SitePresentationSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return transactionTemplate().execute(status -> {
            Optional<Long> currentVersion = currentVersion(settings.id().value());
            if (currentVersion.isEmpty()) {
                insertSettings(settings);
            } else {
                updateSettings(settings, currentVersion.get());
            }
            replaceSupportedLanguages(settings);
            return findById(settings.id()).orElseThrow(() -> new PortfolioPersistenceException(
                    "Saved site presentation settings could not be reloaded: " + settings.id().value()));
        });
    }

    @Override
    public Optional<SitePresentationSettings> findCurrent() {
        return loadSettings("singleton_key = true", Map.of());
    }

    @Override
    public Optional<SitePresentationSettings> findById(SitePresentationSettingsId id) {
        Objects.requireNonNull(id, "id");
        return loadSettings("id = :id", Map.of("id", id.value()));
    }

    private Optional<Long> currentVersion(UUID id) {
        return jdbc().query("""
                SELECT version
                FROM portfolio.site_presentation_settings
                WHERE id = :id
                """, Map.of("id", id), (resultSet, rowNumber) -> resultSet.getLong("version")).stream().findFirst();
    }

    private void insertSettings(SitePresentationSettings settings) {
        jdbc().update("""
                INSERT INTO portfolio.site_presentation_settings (
                    id, singleton_key, site_name, default_language, title_suffix,
                    default_meta_description, default_og_image_asset_id, default_theme,
                    show_featured_projects, show_latest_writing, show_research_highlights,
                    featured_project_limit, latest_writing_limit, updated_at, version
                ) VALUES (
                    :id, true, :siteName, :defaultLanguage, :titleSuffix,
                    :defaultMetaDescription, :defaultOpenGraphImageAssetId, :defaultTheme,
                    :showFeaturedProjects, :showLatestWriting, :showResearchHighlights,
                    :featuredProjectLimit, :latestWritingLimit, :updatedAt, :version
                )
                """, parameters(settings));
    }

    private void updateSettings(SitePresentationSettings settings, long expectedVersion) {
        if (settings.version().value() <= expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Site presentation settings save is stale for id " + settings.id().value());
        }
        int updated = jdbc().update("""
                UPDATE portfolio.site_presentation_settings
                SET site_name = :siteName,
                    default_language = :defaultLanguage,
                    title_suffix = :titleSuffix,
                    default_meta_description = :defaultMetaDescription,
                    default_og_image_asset_id = :defaultOpenGraphImageAssetId,
                    default_theme = :defaultTheme,
                    show_featured_projects = :showFeaturedProjects,
                    show_latest_writing = :showLatestWriting,
                    show_research_highlights = :showResearchHighlights,
                    featured_project_limit = :featuredProjectLimit,
                    latest_writing_limit = :latestWritingLimit,
                    updated_at = :updatedAt,
                    version = :version
                WHERE id = :id AND version = :expectedVersion
                """, parameters(settings).addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                    "Site presentation settings save is stale for id " + settings.id().value());
        }
    }

    private MapSqlParameterSource parameters(SitePresentationSettings settings) {
        HomepageSettings homepage = settings.homepageSettings();
        return new MapSqlParameterSource()
                .addValue("id", settings.id().value())
                .addValue("siteName", settings.siteName().value())
                .addValue("defaultLanguage", settings.defaultLanguage().name())
                .addValue("titleSuffix", settings.titleSuffix().map(titleSuffix -> titleSuffix.value()).orElse(null))
                .addValue("defaultMetaDescription", settings.defaultMetaDescription().map(seoDescription -> seoDescription.value()).orElse(null))
                .addValue("defaultOpenGraphImageAssetId", settings.defaultOpenGraphImageAssetId().map(asset -> asset.value()).orElse(null))
                .addValue("defaultTheme", settings.defaultTheme().name())
                .addValue("showFeaturedProjects", homepage.showFeaturedProjects())
                .addValue("showLatestWriting", homepage.showLatestWriting())
                .addValue("showResearchHighlights", homepage.showResearchHighlights())
                .addValue("featuredProjectLimit", homepage.featuredProjectLimit().value())
                .addValue("latestWritingLimit", homepage.latestWritingLimit().value())
                .addValue("updatedAt", Timestamp.from(settings.updatedAt()))
                .addValue("version", settings.version().value());
    }

    private void replaceSupportedLanguages(SitePresentationSettings settings) {
        jdbc().update("""
                DELETE FROM portfolio.site_supported_languages
                WHERE settings_id = :settingsId
                """, Map.of("settingsId", settings.id().value()));
        MapSqlParameterSource[] batch = settings.supportedLanguages().stream()
                .map(language -> new MapSqlParameterSource()
                        .addValue("settingsId", settings.id().value())
                        .addValue("language", language.name()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO portfolio.site_supported_languages (settings_id, language)
                VALUES (:settingsId, :language)
                """, batch);
    }

    private Optional<SitePresentationSettings> loadSettings(String whereClause, Map<String, Object> params) {
        String sql = """
                SELECT id, site_name, default_language, title_suffix, default_meta_description,
                       default_og_image_asset_id, default_theme, show_featured_projects,
                       show_latest_writing, show_research_highlights, featured_project_limit,
                       latest_writing_limit, updated_at, version
                FROM portfolio.site_presentation_settings
                WHERE %s
                """.formatted(whereClause);
        List<SitePresentationSettings> rows = jdbc().query(sql, params, (resultSet, rowNumber) ->
                mapper.toDomain(resultSet, loadSupportedLanguages(resultSet.getObject("id", UUID.class))));
        return rows.stream().findFirst();
    }

    private Set<ContentLanguage> loadSupportedLanguages(UUID settingsId) {
        return new LinkedHashSet<>(jdbc().query("""
                SELECT language
                FROM portfolio.site_supported_languages
                WHERE settings_id = :settingsId
                ORDER BY language DESC
                """, Map.of("settingsId", settingsId), (resultSet, rowNumber) ->
                ContentLanguage.valueOf(resultSet.getString("language"))));
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new PortfolioPersistenceException("JDBC site presentation settings repository is not available.");
        }
        return available;
    }

    private TransactionTemplate transactionTemplate() {
        TransactionTemplate available = transactions.getIfAvailable();
        if (available == null) {
            throw new PortfolioPersistenceException("JDBC transaction infrastructure is not available.");
        }
        return available;
    }
}
