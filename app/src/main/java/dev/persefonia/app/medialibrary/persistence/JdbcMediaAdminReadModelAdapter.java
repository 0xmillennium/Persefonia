package dev.persefonia.app.medialibrary.persistence;

import dev.persefonia.medialibrary.application.admin.MediaAdminAssetDetails;
import dev.persefonia.medialibrary.application.admin.MediaAdminAssetListItem;
import dev.persefonia.medialibrary.application.admin.MediaAdminAssetValidationResultDetails;
import dev.persefonia.medialibrary.application.admin.MediaAdminAssetVariantDetails;
import dev.persefonia.medialibrary.application.admin.MediaAdminReadModel;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMediaAdminReadModelAdapter implements MediaAdminReadModel {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    JdbcMediaAdminReadModelAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<MediaAdminAssetListItem> listAssets() {
        return jdbc().query("""
                SELECT id, original_filename, kind, visibility, processing_status,
                       content_type, file_extension, size_bytes, checksum,
                       image_width, image_height, created_at, updated_at
                FROM media.assets
                ORDER BY updated_at DESC, created_at DESC
                """, (resultSet, rowNumber) -> listItem(resultSet));
    }

    @Override
    public Optional<MediaAdminAssetDetails> findAssetDetails(AssetId id) {
        Objects.requireNonNull(id, "id");
        List<MediaAdminAssetDetails> rows = jdbc().query("""
                SELECT id, original_filename, kind, visibility, processing_status,
                       content_type, file_extension, size_bytes, checksum,
                       image_width, image_height, created_at, updated_at,
                       alt_text, decorative
                FROM media.assets
                WHERE id = :id
                """, Map.of("id", id.value()), (resultSet, rowNumber) -> {
            MediaAdminAssetListItem summary = listItem(resultSet);
            return new MediaAdminAssetDetails(
                    summary,
                    resultSet.getString("alt_text"),
                    resultSet.getBoolean("decorative"),
                    variants(summary),
                    validationResults(summary.assetId()));
        });
        return rows.stream().findFirst();
    }

    private List<MediaAdminAssetVariantDetails> variants(MediaAdminAssetListItem asset) {
        return jdbc().query("""
                SELECT name, width, height, content_type, size_bytes, checksum
                FROM media.asset_variants
                WHERE asset_id = :assetId
                ORDER BY CASE name
                    WHEN 'thumbnail' THEN 1
                    WHEN 'medium' THEN 2
                    WHEN 'large' THEN 3
                    WHEN 'og' THEN 4
                    ELSE 5
                END
                """, Map.of("assetId", asset.assetId().value()), (resultSet, rowNumber) -> variant(resultSet, asset));
    }

    private List<MediaAdminAssetValidationResultDetails> validationResults(AssetId assetId) {
        return jdbc().query("""
                SELECT rule, status, message, checked_at
                FROM media.asset_validation_results
                WHERE asset_id = :assetId
                ORDER BY rule
                """, Map.of("assetId", assetId.value()), (resultSet, rowNumber) ->
                new MediaAdminAssetValidationResultDetails(
                        resultSet.getString("rule"),
                        resultSet.getString("status"),
                        resultSet.getString("message"),
                        instant(resultSet, "checked_at")));
    }

    private static MediaAdminAssetListItem listItem(ResultSet resultSet) throws SQLException {
        return new MediaAdminAssetListItem(
                AssetId.from(resultSet.getObject("id", UUID.class)),
                resultSet.getString("original_filename"),
                AssetKind.valueOf(resultSet.getString("kind")),
                AssetVisibility.valueOf(resultSet.getString("visibility")),
                ProcessingStatus.valueOf(resultSet.getString("processing_status")),
                resultSet.getString("content_type"),
                resultSet.getString("file_extension"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("checksum"),
                nullableInteger(resultSet, "image_width"),
                nullableInteger(resultSet, "image_height"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static MediaAdminAssetVariantDetails variant(ResultSet resultSet, MediaAdminAssetListItem asset)
            throws SQLException {
        String name = resultSet.getString("name");
        return new MediaAdminAssetVariantDetails(
                name,
                resultSet.getInt("width"),
                resultSet.getInt("height"),
                resultSet.getString("content_type"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("checksum"),
                publicRoute(asset, name));
    }

    private static String publicRoute(MediaAdminAssetListItem asset, String variantName) {
        if (asset.visibility() != AssetVisibility.PUBLIC || asset.processingStatus() != ProcessingStatus.PROCESSED) {
            return null;
        }
        return "/media/assets/" + asset.assetId().value() + "/variants/" + variantName;
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp.toInstant();
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new MediaLibraryPersistenceException("JDBC media admin read model is not available.");
        }
        return available;
    }
}
