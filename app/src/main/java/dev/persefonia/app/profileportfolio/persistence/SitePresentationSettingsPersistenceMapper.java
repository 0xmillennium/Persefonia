package dev.persefonia.app.profileportfolio.persistence;

import dev.persefonia.profileportfolio.domain.common.AssetId;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.Version;
import dev.persefonia.profileportfolio.domain.settings.HomepageSettings;
import dev.persefonia.profileportfolio.domain.settings.PositiveInteger;
import dev.persefonia.profileportfolio.domain.settings.SeoDescription;
import dev.persefonia.profileportfolio.domain.settings.SiteName;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsId;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
import dev.persefonia.profileportfolio.domain.settings.TitleSuffix;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

final class SitePresentationSettingsPersistenceMapper {
    SitePresentationSettings toDomain(ResultSet resultSet, Set<ContentLanguage> supportedLanguages) throws SQLException {
        return SitePresentationSettings.rehydrate(
                SitePresentationSettingsId.from(resultSet.getObject("id", UUID.class)),
                SiteName.of(resultSet.getString("site_name")),
                ContentLanguage.valueOf(resultSet.getString("default_language")),
                supportedLanguages,
                nullable(resultSet.getString("title_suffix"), TitleSuffix::of),
                nullable(resultSet.getString("default_meta_description"), SeoDescription::of),
                nullable(resultSet.getObject("default_og_image_asset_id", UUID.class), AssetId::from),
                ThemePreference.valueOf(resultSet.getString("default_theme")),
                HomepageSettings.of(
                        resultSet.getBoolean("show_featured_projects"),
                        resultSet.getBoolean("show_latest_writing"),
                        resultSet.getBoolean("show_research_highlights"),
                        PositiveInteger.of(resultSet.getInt("featured_project_limit")),
                        PositiveInteger.of(resultSet.getInt("latest_writing_limit"))),
                timestamp(resultSet, "updated_at"),
                Version.of(resultSet.getLong("version")));
    }

    private Instant timestamp(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private <T> T nullable(String value, java.util.function.Function<String, T> mapper) {
        return value == null ? null : mapper.apply(value);
    }

    private <T> T nullable(UUID value, java.util.function.Function<UUID, T> mapper) {
        return value == null ? null : mapper.apply(value);
    }
}
