package dev.persefonia.app.medialibrary.persistence;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetValidationResult;
import dev.persefonia.medialibrary.domain.asset.AssetVariant;
import dev.persefonia.medialibrary.domain.asset.Checksum;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcAssetRepositoryAdapter implements AssetRepository {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final ObjectProvider<TransactionTemplate> transactions;
    private final AssetPersistenceMapper mapper = new AssetPersistenceMapper();

    JdbcAssetRepositoryAdapter(
            ObjectProvider<NamedParameterJdbcTemplate> jdbc,
            ObjectProvider<TransactionTemplate> transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public Asset save(Asset asset) {
        Objects.requireNonNull(asset, "asset");
        return transactionTemplate().execute(status -> {
            Optional<Long> currentVersion = currentVersion(asset.id().value());
            if (currentVersion.isEmpty()) {
                insertAsset(asset);
            } else {
                updateAsset(asset, currentVersion.get());
            }
            replaceChildren(asset);
            return findById(asset.id()).orElseThrow(() -> new MediaLibraryPersistenceException(
                    "Saved asset could not be reloaded: " + asset.id().value()));
        });
    }

    @Override
    public Optional<Asset> findById(AssetId id) {
        Objects.requireNonNull(id, "id");
        return loadAsset("assets.id = :id", Map.of("id", id.value()));
    }

    @Override
    public Optional<Asset> findByChecksum(Checksum checksum) {
        Objects.requireNonNull(checksum, "checksum");
        return loadAsset("assets.checksum = :checksum", Map.of("checksum", checksum.value()));
    }

    private Optional<Long> currentVersion(UUID id) {
        return jdbc().query("""
                SELECT version
                FROM media.assets
                WHERE id = :id
                """, Map.of("id", id), (resultSet, rowNumber) -> resultSet.getLong("version"))
                .stream()
                .findFirst();
    }

    private void insertAsset(Asset asset) {
        jdbc().update("""
                INSERT INTO media.assets (
                    id, original_filename, stored_filename, storage_path, public_url,
                    content_type, file_extension, size_bytes, checksum, kind, visibility,
                    image_width, image_height, alt_text, decorative, processing_status,
                    created_at, updated_at, version
                ) VALUES (
                    :id, :originalFilename, :storedFilename, :storagePath, :publicUrl,
                    :contentType, :fileExtension, :sizeBytes, :checksum, :kind, :visibility,
                    :imageWidth, :imageHeight, :altText, :decorative, :processingStatus,
                    :createdAt, :updatedAt, :version
                )
                """, parameters(asset));
    }

    private void updateAsset(Asset asset, long expectedVersion) {
        if (asset.version().value() <= expectedVersion) {
            throw new OptimisticLockingFailureException("Asset save is stale for id " + asset.id().value());
        }
        int updated = jdbc().update("""
                UPDATE media.assets
                SET original_filename = :originalFilename,
                    stored_filename = :storedFilename,
                    storage_path = :storagePath,
                    public_url = :publicUrl,
                    content_type = :contentType,
                    file_extension = :fileExtension,
                    size_bytes = :sizeBytes,
                    checksum = :checksum,
                    kind = :kind,
                    visibility = :visibility,
                    image_width = :imageWidth,
                    image_height = :imageHeight,
                    alt_text = :altText,
                    decorative = :decorative,
                    processing_status = :processingStatus,
                    updated_at = :updatedAt,
                    version = :version
                WHERE id = :id AND version = :expectedVersion
                """, parameters(asset).addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Asset save is stale for id " + asset.id().value());
        }
    }

    private MapSqlParameterSource parameters(Asset asset) {
        return new MapSqlParameterSource()
                .addValue("id", asset.id().value())
                .addValue("originalFilename", asset.originalFilename().value())
                .addValue("storedFilename", asset.storedFilename().value())
                .addValue("storagePath", asset.storagePath().value())
                .addValue("publicUrl", asset.publicUrl().map(value -> value.value()).orElse(null))
                .addValue("contentType", asset.contentType().value())
                .addValue("fileExtension", asset.fileExtension().value())
                .addValue("sizeBytes", asset.sizeBytes().value())
                .addValue("checksum", asset.checksum().value())
                .addValue("kind", asset.kind().name())
                .addValue("visibility", asset.visibility().name())
                .addValue("imageWidth", asset.imageDimensions().map(value -> value.width().value()).orElse(null))
                .addValue("imageHeight", asset.imageDimensions().map(value -> value.height().value()).orElse(null))
                .addValue("altText", asset.altText().map(value -> value.value()).orElse(null))
                .addValue("decorative", asset.decorative().value())
                .addValue("processingStatus", asset.processingStatus().name())
                .addValue("createdAt", Timestamp.from(asset.createdAt()))
                .addValue("updatedAt", Timestamp.from(asset.updatedAt()))
                .addValue("version", asset.version().value());
    }

    private void replaceChildren(Asset asset) {
        jdbc().update("""
                DELETE FROM media.asset_variants
                WHERE asset_id = :assetId
                """, Map.of("assetId", asset.id().value()));
        jdbc().update("""
                DELETE FROM media.asset_validation_results
                WHERE asset_id = :assetId
                """, Map.of("assetId", asset.id().value()));
        insertVariants(asset);
        insertValidationResults(asset);
    }

    private void insertVariants(Asset asset) {
        MapSqlParameterSource[] batch = asset.variants().stream()
                .map(variant -> variantParameters(asset.id(), variant))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO media.asset_variants (
                    id, asset_id, name, width, height, content_type, size_bytes,
                    storage_path, public_url, checksum, created_at
                ) VALUES (
                    :id, :assetId, :name, :width, :height, :contentType, :sizeBytes,
                    :storagePath, :publicUrl, :checksum, :createdAt
                )
                """, batch);
    }

    private MapSqlParameterSource variantParameters(AssetId assetId, AssetVariant variant) {
        return new MapSqlParameterSource()
                .addValue("id", variant.id().value())
                .addValue("assetId", assetId.value())
                .addValue("name", variant.name().databaseValue())
                .addValue("width", variant.width().value())
                .addValue("height", variant.height().value())
                .addValue("contentType", variant.contentType().value())
                .addValue("sizeBytes", variant.sizeBytes().value())
                .addValue("storagePath", variant.storagePath().value())
                .addValue("publicUrl", variant.publicUrlOptional().map(value -> value.value()).orElse(null))
                .addValue("checksum", variant.checksum().value())
                .addValue("createdAt", Timestamp.from(variant.createdAt()));
    }

    private void insertValidationResults(Asset asset) {
        MapSqlParameterSource[] batch = asset.validationResults().stream()
                .map(result -> validationParameters(asset.id(), result))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO media.asset_validation_results (
                    id, asset_id, rule, status, message, checked_at
                ) VALUES (
                    :id, :assetId, :rule, :status, :message, :checkedAt
                )
                """, batch);
    }

    private MapSqlParameterSource validationParameters(AssetId assetId, AssetValidationResult result) {
        return new MapSqlParameterSource()
                .addValue("id", result.id().value())
                .addValue("assetId", assetId.value())
                .addValue("rule", result.rule().value())
                .addValue("status", result.status().name())
                .addValue("message", result.messageOptional().map(value -> value.value()).orElse(null))
                .addValue("checkedAt", Timestamp.from(result.checkedAt()));
    }

    private Optional<Asset> loadAsset(String whereClause, Map<String, Object> parameters) {
        String sql = """
                SELECT id, original_filename, stored_filename, storage_path, public_url,
                       content_type, file_extension, size_bytes, checksum, kind, visibility,
                       image_width, image_height, alt_text, decorative, processing_status,
                       created_at, updated_at, version
                FROM media.assets assets
                WHERE %s
                """.formatted(whereClause);
        return jdbc().query(sql, parameters, (resultSet, rowNumber) -> {
            UUID assetId = resultSet.getObject("id", UUID.class);
            return mapper.toDomain(resultSet, loadVariants(assetId), loadValidationResults(assetId));
        }).stream().findFirst();
    }

    private List<AssetVariant> loadVariants(UUID assetId) {
        return jdbc().query("""
                SELECT id, name, width, height, content_type, size_bytes,
                       storage_path, public_url, checksum, created_at
                FROM media.asset_variants
                WHERE asset_id = :assetId
                ORDER BY CASE name
                    WHEN 'thumbnail' THEN 1
                    WHEN 'medium' THEN 2
                    WHEN 'large' THEN 3
                    WHEN 'og' THEN 4
                END
                """, Map.of("assetId", assetId), (resultSet, rowNumber) -> mapper.variant(resultSet));
    }

    private List<AssetValidationResult> loadValidationResults(UUID assetId) {
        return jdbc().query("""
                SELECT id, rule, status, message, checked_at
                FROM media.asset_validation_results
                WHERE asset_id = :assetId
                ORDER BY rule
                """, Map.of("assetId", assetId), (resultSet, rowNumber) -> mapper.validationResult(resultSet));
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new MediaLibraryPersistenceException("JDBC asset repository is not available.");
        }
        return available;
    }

    private TransactionTemplate transactionTemplate() {
        TransactionTemplate available = transactions.getIfAvailable();
        if (available == null) {
            throw new MediaLibraryPersistenceException("JDBC transaction infrastructure is not available.");
        }
        return available;
    }
}
