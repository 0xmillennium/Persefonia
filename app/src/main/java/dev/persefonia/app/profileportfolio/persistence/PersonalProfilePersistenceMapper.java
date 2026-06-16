package dev.persefonia.app.profileportfolio.persistence;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.common.Version;
import dev.persefonia.profileportfolio.domain.profile.CurrentFocusItem;
import dev.persefonia.profileportfolio.domain.profile.CurrentFocusItemId;
import dev.persefonia.profileportfolio.domain.profile.DisplayName;
import dev.persefonia.profileportfolio.domain.profile.EducationDescription;
import dev.persefonia.profileportfolio.domain.profile.EducationSummary;
import dev.persefonia.profileportfolio.domain.profile.EducationSummaryId;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLink;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLinkId;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.profile.FocusAreaDescription;
import dev.persefonia.profileportfolio.domain.profile.FocusAreaName;
import dev.persefonia.profileportfolio.domain.profile.FocusItemText;
import dev.persefonia.profileportfolio.domain.profile.InstitutionName;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
import dev.persefonia.profileportfolio.domain.profile.LocationText;
import dev.persefonia.profileportfolio.domain.profile.LongBio;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfile;
import dev.persefonia.profileportfolio.domain.profile.ProfileId;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalization;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalizationId;
import dev.persefonia.profileportfolio.domain.profile.ProgramName;
import dev.persefonia.profileportfolio.domain.profile.ShortBio;
import dev.persefonia.profileportfolio.domain.profile.TechnicalFocusArea;
import dev.persefonia.profileportfolio.domain.profile.TechnicalFocusAreaId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class PersonalProfilePersistenceMapper {
    PersonalProfile toDomain(
            ResultSet resultSet,
            List<ProfileLocalization> localizations,
            List<ExternalProfileLink> links) throws SQLException {
        return PersonalProfile.rehydrate(
                ProfileId.from(resultSet.getObject("id", UUID.class)),
                DisplayName.of(resultSet.getString("display_name")),
                resultSet.getBoolean("active"),
                localizations,
                links,
                timestamp(resultSet, "created_at"),
                timestamp(resultSet, "updated_at"),
                Version.of(resultSet.getLong("version")));
    }

    ProfileLocalization localization(
            ResultSet resultSet,
            List<TechnicalFocusArea> focusAreas,
            List<EducationSummary> educationSummaries,
            List<CurrentFocusItem> currentFocusItems) throws SQLException {
        return new ProfileLocalization(
                ProfileLocalizationId.from(resultSet.getObject("id", UUID.class)),
                ContentLanguage.valueOf(resultSet.getString("language")),
                ShortBio.of(resultSet.getString("short_bio")),
                LongBio.of(resultSet.getString("long_bio")),
                nullable(resultSet.getString("location_text"), LocationText::of),
                focusAreas,
                educationSummaries,
                currentFocusItems);
    }

    ExternalProfileLink externalLink(ResultSet resultSet) throws SQLException {
        return new ExternalProfileLink(
                ExternalProfileLinkId.from(resultSet.getObject("id", UUID.class)),
                LinkLabel.of(resultSet.getString("label")),
                ExternalUrl.of(resultSet.getString("url")),
                SortOrder.of(resultSet.getInt("sort_order")));
    }

    TechnicalFocusArea focusArea(ResultSet resultSet) throws SQLException {
        return new TechnicalFocusArea(
                TechnicalFocusAreaId.from(resultSet.getObject("id", UUID.class)),
                FocusAreaName.of(resultSet.getString("name")),
                nullable(resultSet.getString("description"), FocusAreaDescription::of),
                SortOrder.of(resultSet.getInt("sort_order")));
    }

    EducationSummary educationSummary(ResultSet resultSet) throws SQLException {
        return new EducationSummary(
                EducationSummaryId.from(resultSet.getObject("id", UUID.class)),
                InstitutionName.of(resultSet.getString("institution")),
                ProgramName.of(resultSet.getString("program")),
                nullable(resultSet.getString("description"), EducationDescription::of),
                SortOrder.of(resultSet.getInt("sort_order")));
    }

    CurrentFocusItem currentFocusItem(ResultSet resultSet) throws SQLException {
        return new CurrentFocusItem(
                CurrentFocusItemId.from(resultSet.getObject("id", UUID.class)),
                FocusItemText.of(resultSet.getString("text")),
                SortOrder.of(resultSet.getInt("sort_order")));
    }

    private Instant timestamp(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private <T> T nullable(String value, java.util.function.Function<String, T> mapper) {
        return value == null ? null : mapper.apply(value);
    }
}
