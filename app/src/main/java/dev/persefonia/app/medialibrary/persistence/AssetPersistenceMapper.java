package dev.persefonia.app.medialibrary.persistence;

import dev.persefonia.medialibrary.domain.asset.AltText;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import dev.persefonia.medialibrary.domain.asset.AssetValidationResult;
import dev.persefonia.medialibrary.domain.asset.AssetValidationResultId;
import dev.persefonia.medialibrary.domain.asset.AssetVariant;
import dev.persefonia.medialibrary.domain.asset.AssetVariantId;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.Checksum;
import dev.persefonia.medialibrary.domain.asset.ContentTypeName;
import dev.persefonia.medialibrary.domain.asset.DecorativeImageFlag;
import dev.persefonia.medialibrary.domain.asset.FileExtension;
import dev.persefonia.medialibrary.domain.asset.FileSize;
import dev.persefonia.medialibrary.domain.asset.ImageDimensions;
import dev.persefonia.medialibrary.domain.asset.OriginalFilename;
import dev.persefonia.medialibrary.domain.asset.PixelHeight;
import dev.persefonia.medialibrary.domain.asset.PixelWidth;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import dev.persefonia.medialibrary.domain.asset.PublicAssetUrl;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import dev.persefonia.medialibrary.domain.asset.StoredFilename;
import dev.persefonia.medialibrary.domain.asset.ValidationMessage;
import dev.persefonia.medialibrary.domain.asset.ValidationRuleName;
import dev.persefonia.medialibrary.domain.asset.ValidationStatus;
import dev.persefonia.medialibrary.domain.asset.VariantName;
import dev.persefonia.medialibrary.domain.asset.Version;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssetPersistenceMapper {
    record AssetRow(
            AssetId id,
            OriginalFilename originalFilename,
            StoredFilename storedFilename,
            StoragePath storagePath,
            PublicAssetUrl publicUrl,
            ContentTypeName contentType,
            FileExtension fileExtension,
            FileSize size,
            Checksum checksum,
            AssetKind kind,
            AssetVisibility visibility,
            ImageDimensions dimensions,
            AltText altText,
            DecorativeImageFlag decorative,
            ProcessingStatus status,
            Instant createdAt,
            Instant updatedAt,
            Version version) {}

    AssetRow assetRow(ResultSet resultSet) throws SQLException {
        return new AssetRow(
                AssetId.from(resultSet.getObject("id", UUID.class)),
                OriginalFilename.of(resultSet.getString("original_filename")),
                StoredFilename.of(resultSet.getString("stored_filename")),
                StoragePath.of(resultSet.getString("storage_path")),
                nullableString(resultSet, "public_url", PublicAssetUrl::of),
                ContentTypeName.of(resultSet.getString("content_type")),
                FileExtension.of(resultSet.getString("file_extension")),
                FileSize.of(resultSet.getLong("size_bytes")),
                Checksum.of(resultSet.getString("checksum")),
                AssetKind.valueOf(resultSet.getString("kind")),
                AssetVisibility.valueOf(resultSet.getString("visibility")),
                imageDimensions(nullableInteger(resultSet, "image_width"), nullableInteger(resultSet, "image_height")),
                nullableString(resultSet, "alt_text", AltText::of),
                new DecorativeImageFlag(resultSet.getBoolean("decorative")),
                ProcessingStatus.valueOf(resultSet.getString("processing_status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                Version.of(resultSet.getLong("version")));
    }

    Asset toDomain(
            AssetRow row,
            List<AssetVariant> variants,
            List<AssetValidationResult> validationResults) {
        return Asset.rehydrate(
                row.id(),
                row.originalFilename(),
                row.storedFilename(),
                row.storagePath(),
                row.publicUrl(),
                row.contentType(),
                row.fileExtension(),
                row.size(),
                row.checksum(),
                row.kind(),
                row.visibility(),
                row.dimensions(),
                row.altText(),
                row.decorative(),
                row.status(),
                variants,
                validationResults,
                row.createdAt(),
                row.updatedAt(),
                row.version());
    }

    AssetVariant variant(ResultSet resultSet) throws SQLException {
        return new AssetVariant(
                AssetVariantId.from(resultSet.getObject("id", UUID.class)),
                VariantName.fromDatabaseValue(resultSet.getString("name")),
                PixelWidth.of(resultSet.getInt("width")),
                PixelHeight.of(resultSet.getInt("height")),
                ContentTypeName.of(resultSet.getString("content_type")),
                FileSize.of(resultSet.getLong("size_bytes")),
                StoragePath.of(resultSet.getString("storage_path")),
                nullableString(resultSet, "public_url", PublicAssetUrl::of),
                Checksum.of(resultSet.getString("checksum")),
                resultSet.getTimestamp("created_at").toInstant());
    }

    AssetValidationResult validationResult(ResultSet resultSet) throws SQLException {
        return new AssetValidationResult(
                AssetValidationResultId.from(resultSet.getObject("id", UUID.class)),
                ValidationRuleName.of(resultSet.getString("rule")),
                ValidationStatus.valueOf(resultSet.getString("status")),
                nullableString(resultSet, "message", ValidationMessage::of),
                resultSet.getTimestamp("checked_at").toInstant());
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private ImageDimensions imageDimensions(Integer width, Integer height) {
        if (width == null && height == null) {
            return null;
        }
        if (width == null || height == null) {
            throw new MediaLibraryPersistenceException("Asset image dimensions must have both width and height");
        }
        return ImageDimensions.of(width, height);
    }

    private <T> T nullableString(ResultSet resultSet, String column, java.util.function.Function<String, T> mapper)
            throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : mapper.apply(value);
    }
}
