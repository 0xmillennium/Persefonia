package dev.persefonia.app.profileportfolio.persistence;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.Version;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvDocument;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvDocumentId;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfile;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileId;
import dev.persefonia.profileportfolio.domain.cv.CvDisplayLabel;
import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class ActiveCvProfilePersistenceMapper {
    record ProfileRow(
            ActiveCvProfileId id,
            Instant createdAt,
            Instant updatedAt,
            Version version) {}

    ProfileRow profileRow(ResultSet profileRow) throws SQLException {
        return new ProfileRow(
                ActiveCvProfileId.from(profileRow.getObject("id", UUID.class)),
                timestamp(profileRow, "created_at"),
                timestamp(profileRow, "updated_at"),
                Version.of(profileRow.getLong("version")));
    }

    ActiveCvProfile toDomain(ProfileRow row, List<ActiveCvDocument> documents) {
        return ActiveCvProfile.rehydrate(
                row.id(),
                documents,
                row.createdAt(),
                row.updatedAt(),
                row.version());
    }

    ActiveCvDocument document(ResultSet resultSet) throws SQLException {
        return new ActiveCvDocument(
                ActiveCvDocumentId.from(resultSet.getObject("id", UUID.class)),
                ContentLanguage.valueOf(resultSet.getString("language")),
                MediaAssetId.from(resultSet.getObject("asset_id", UUID.class)),
                nullable(resultSet.getString("display_label"), CvDisplayLabel::of),
                timestamp(resultSet, "selected_at"),
                timestamp(resultSet, "created_at"),
                timestamp(resultSet, "updated_at"));
    }

    private Instant timestamp(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private <T> T nullable(String value, java.util.function.Function<String, T> mapper) {
        return value == null ? null : mapper.apply(value);
    }
}
