package dev.persefonia.app.medialibrary.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MediaLibraryMigrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-18T10:00:00Z");

    @BeforeAll
    static void migrateDatabase() {
        MediaLibraryMigrationDatabase.start();
        MediaLibraryMigrationDatabase.cleanMigrate();
    }

    @BeforeEach
    void truncateMedia() throws SQLException {
        MediaLibrarySql.update("TRUNCATE media.assets CASCADE");
    }

    @Test
    void cleanMigrateCreatesMediaLibraryTables() throws SQLException {
        assertThat(MediaLibrarySql.strings("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'media'
                  AND table_name IN ('assets', 'asset_variants', 'asset_validation_results')
                """)).containsExactlyInAnyOrder("assets", "asset_variants", "asset_validation_results");
    }

    @Test
    void publicImageAccessibilityAndProcessingConstraintsRejectImpossibleStates() {
        assertRejected(() -> insertAsset(UUID.randomUUID(), "a", "a", "PUBLIC", "PROCESSED", 100, 100, null, false));
        assertRejected(() -> insertAsset(UUID.randomUUID(), "b", "b", "PUBLIC", "PENDING", null, null, "Alt", false));
        assertRejected(() -> insertAsset(UUID.randomUUID(), "c", "c", "PUBLIC", "FAILED", 100, 100, "Alt", false));
    }

    @Test
    void processedImageRequiresDimensionsAndFailedImageMayOmitThem() throws SQLException {
        assertRejected(() -> insertAsset(UUID.randomUUID(), "a", "a", "PRIVATE", "PROCESSED", null, null, null, false));
        insertAsset(UUID.randomUUID(), "b", "b", "PRIVATE", "FAILED", null, null, null, false);
    }

    @Test
    void imageDimensionsAreStoredAsAnAtomicPair() {
        assertRejected(() -> insertAsset(
                UUID.randomUUID(), "partial", "partial", "PRIVATE", "PENDING", 100, null, null, false));
    }

    @Test
    void assetChecksumAndStoragePathAreUnique() throws SQLException {
        insertAsset(UUID.randomUUID(), "same", "same", "PRIVATE", "PENDING", null, null, null, false);
        assertRejected(() -> insertAsset(UUID.randomUUID(), "same", "other", "PRIVATE", "PENDING", null, null, null, false));
        assertRejected(() -> insertAsset(UUID.randomUUID(), "other", "same", "PRIVATE", "PENDING", null, null, null, false));
    }

    @Test
    void childUniquenessConstraintsAreEnforced() throws SQLException {
        UUID assetId = UUID.randomUUID();
        insertAsset(assetId, "root", "root", "PRIVATE", "PROCESSED", 100, 100, null, false);
        insertVariant(UUID.randomUUID(), assetId, "thumbnail", "variant-one");
        assertRejected(() -> insertVariant(UUID.randomUUID(), assetId, "thumbnail", "variant-two"));
        assertRejected(() -> insertVariant(UUID.randomUUID(), assetId, "medium", "variant-one"));
        insertValidation(UUID.randomUUID(), assetId, "mime");
        assertRejected(() -> insertValidation(UUID.randomUUID(), assetId, "mime"));
    }

    @Test
    void childrenCascadeDeleteWithAsset() throws SQLException {
        UUID assetId = UUID.randomUUID();
        insertAsset(assetId, "root", "root", "PRIVATE", "PROCESSED", 100, 100, null, false);
        insertVariant(UUID.randomUUID(), assetId, "thumbnail", "variant-one");
        insertValidation(UUID.randomUUID(), assetId, "mime");

        MediaLibrarySql.update("DELETE FROM media.assets WHERE id = ?", assetId);

        assertThat(MediaLibrarySql.count("SELECT count(*) FROM media.asset_variants")).isZero();
        assertThat(MediaLibrarySql.count("SELECT count(*) FROM media.asset_validation_results")).isZero();
    }

    @Test
    void mediaForeignKeysStayWithinMediaSchema() throws SQLException {
        assertThat(MediaLibrarySql.count("""
                SELECT count(*)
                FROM information_schema.table_constraints constraints
                JOIN information_schema.constraint_column_usage usage
                  ON usage.constraint_name = constraints.constraint_name
                 AND usage.constraint_schema = constraints.constraint_schema
                WHERE constraints.constraint_type = 'FOREIGN KEY'
                  AND constraints.table_schema = 'media'
                  AND usage.table_schema <> 'media'
                """)).isZero();
    }

    private static void insertAsset(
            UUID id,
            String checksum,
            String pathSuffix,
            String visibility,
            String status,
            Integer width,
            Integer height,
            String altText,
            boolean decorative) throws SQLException {
        MediaLibrarySql.update("""
                INSERT INTO media.assets (
                    id, original_filename, stored_filename, storage_path, content_type,
                    file_extension, size_bytes, checksum, kind, visibility, image_width,
                    image_height, alt_text, decorative, processing_status, created_at,
                    updated_at, version
                ) VALUES (?, 'asset.png', 'stored.png', ?, 'image/png', 'png', 100, ?,
                    'IMAGE', ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, "media/" + pathSuffix + ".png", checksum, visibility, width, height,
                altText, decorative, status, NOW, NOW);
    }

    private static void insertVariant(UUID id, UUID assetId, String name, String pathSuffix) throws SQLException {
        MediaLibrarySql.update("""
                INSERT INTO media.asset_variants (
                    id, asset_id, name, width, height, content_type, size_bytes,
                    storage_path, checksum, created_at
                ) VALUES (?, ?, ?, 50, 50, 'image/webp', 20, ?, ?, ?)
                """, id, assetId, name, "media/" + pathSuffix + ".webp", "checksum-" + id, NOW);
    }

    private static void insertValidation(UUID id, UUID assetId, String rule) throws SQLException {
        MediaLibrarySql.update("""
                INSERT INTO media.asset_validation_results (
                    id, asset_id, rule, status, checked_at
                ) VALUES (?, ?, ?, 'PASSED', ?)
                """, id, assetId, rule, NOW);
    }

    private static void assertRejected(SqlOperation operation) {
        assertThatThrownBy(operation::run).isInstanceOf(SQLException.class);
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
