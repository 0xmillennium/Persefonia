package dev.persefonia.app.platformoperations.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.persefonia.app.medialibrary.recovery.JdbcMediaRecoveryInventoryReadAdapter;
import dev.persefonia.app.medialibrary.storage.LocalFileAssetStorageAdapter;
import dev.persefonia.app.medialibrary.storage.MediaStorageReadinessService;
import dev.persefonia.medialibrary.application.recovery.*;
import dev.persefonia.platformoperations.application.operations.*;
import dev.persefonia.platformoperations.application.recovery.*;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

class RecoveryReadAdaptersIntegrationTest {
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.integrationDatabase();
    private static JdbcTemplate jdbc;
    private static NamedParameterJdbcTemplate namedJdbc;
    @TempDir Path temporaryStorage;

    @BeforeAll
    static void migrate() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        namedJdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Test
    void mediaInventoryUsesStableKeysetPagesAndIncludesAllPersistedStatesAndVariants() {
        for (int index = 0; index < 405; index++) {
            insertAsset(new UUID(0, index + 1L), index % 2 == 0 ? "PRIVATE" : "PUBLIC",
                    switch (index % 3) { case 0 -> "PENDING"; case 1 -> "FAILED"; default -> "NOT_REQUIRED"; },
                    String.format("%064x", index + 1));
        }
        UUID variantAsset = new UUID(0, 1);
        insertVariant(UUID.fromString("10000000-0000-0000-0000-000000000001"), variantAsset, "thumbnail", "a".repeat(64));
        insertVariant(UUID.fromString("10000000-0000-0000-0000-000000000002"), variantAsset, "medium", "b".repeat(64));
        JdbcMediaRecoveryInventoryReadAdapter adapter = new JdbcMediaRecoveryInventoryReadAdapter(provider());

        List<MediaRecoveryObjectReference> firstTraversal = traverse(adapter);
        List<MediaRecoveryObjectReference> secondTraversal = traverse(adapter);

        assertThat(firstTraversal).hasSize(407).doesNotHaveDuplicates();
        assertThat(firstTraversal.stream().filter(item -> item.kind() == MediaRecoveryObjectKind.ORIGINAL)).hasSize(405);
        assertThat(firstTraversal.stream().filter(item -> item.kind() == MediaRecoveryObjectKind.VARIANT)).hasSize(2);
        assertThat(firstTraversal).containsExactlyElementsOf(secondTraversal);
    }

    @Test
    void durableReferenceProjectionDetectsEveryRequiredSourceWithoutCrossContextForeignKeys() {
        UUID missing = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID content = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO publishing.content_items
                    (id, type, status, visibility, language, og_image_asset_id, created_at, updated_at, version)
                VALUES (?, 'ARTICLE', 'DRAFT', 'PRIVATE', 'EN', ?, now(), now(), 0)
                """, content, missing);
        jdbc.update("""
                INSERT INTO publishing.content_revisions
                    (id, content_item_id, revision_number, revision_type, title, slug, summary,
                     markdown_source, og_image_asset_id, created_by_admin_ref, created_at)
                VALUES (?, ?, 1, 'MANUAL_SNAPSHOT', 'Title', 'title', 'Summary', '# Body', ?, ?, now())
                """, UUID.randomUUID(), content, missing, UUID.randomUUID());
        jdbc.update("UPDATE portfolio.site_presentation_settings SET default_og_image_asset_id = ?", missing);
        jdbc.update("""
                INSERT INTO portfolio.projects
                    (id, status, visibility, featured, cover_asset_id, created_at, updated_at, version)
                VALUES (?, 'ACTIVE', 'PRIVATE', false, ?, now(), now(), 0)
                """, UUID.randomUUID(), missing);
        jdbc.update("""
                INSERT INTO portfolio.active_cv_documents
                    (id, active_cv_profile_id, language, asset_id, selected_at, created_at, updated_at)
                VALUES (?, '00000000-0000-0000-0000-000000000801', 'EN', ?, now(), now(), now())
                """, UUID.randomUUID(), missing);
        jdbc.update("""
                INSERT INTO discovery.discoverable_resources
                    (id, source_context, source_type, source_entity_id, resource_type, route_purpose,
                     language, public_url, canonical_url, title, summary, indexing_policy,
                     search_eligibility, sitemap_eligibility, feed_eligibility, og_image_asset_id,
                     search_text, created_at, version)
                VALUES (?, 'CONTENT_PUBLISHING', 'CONTENT_ITEM', ?, 'ARTICLE', 'DETAIL', 'EN',
                        '/dangling', '/dangling', 'Title', 'Summary', 'NO_INDEX', 'NOT_ELIGIBLE',
                        'NOT_ELIGIBLE', 'NOT_ELIGIBLE', ?, 'search', now(), 0)
                """, UUID.randomUUID(), content, missing);

        DurableAssetReferenceIntegritySummary summary =
                new JdbcDurableAssetReferenceIntegrityReadAdapter(provider()).verify();

        assertThat(summary.totalReferences()).isEqualTo(6);
        assertThat(summary.danglingReferences()).isEqualTo(6);
        assertThat(summary.reportedIssues()).extracting(DurableAssetReferenceIssue::referenceKind)
                .containsExactlyInAnyOrder(DurableAssetReferenceKind.values());
    }

    @Test
    void realDatabaseAndFilesystemRepresentativeRecoveryUnitIsConsistent() throws Exception {
        UUID image = UUID.randomUUID();
        UUID pdf = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        byte[] imageBytes = new byte[] {1, 2, 3};
        byte[] pdfBytes = new byte[] {4, 5, 6, 7};
        byte[] variantBytes = new byte[] {8, 9};
        insertRecoverableAsset(image, "IMAGE", "PRIVATE", "PROCESSED", imageBytes, true);
        insertRecoverableAsset(pdf, "PDF", "PRIVATE", "NOT_REQUIRED", pdfBytes, false);
        jdbc.update("""
                INSERT INTO media.asset_variants
                    (id, asset_id, name, width, height, content_type, size_bytes, storage_path, checksum, created_at)
                VALUES (?, ?, 'thumbnail', 1, 1, 'image/webp', ?, ?, ?, now())
                """, variant, image, variantBytes.length, "variants/" + variant, checksum(variantBytes));

        UUID content = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO publishing.content_items
                    (id, type, status, visibility, language, og_image_asset_id, created_at, updated_at, version)
                VALUES (?, 'ARTICLE', 'DRAFT', 'PRIVATE', 'EN', ?, now(), now(), 0)
                """, content, image);
        jdbc.update("""
                INSERT INTO portfolio.projects
                    (id, status, visibility, featured, cover_asset_id, created_at, updated_at, version)
                VALUES (?, 'ACTIVE', 'PRIVATE', false, ?, now(), now(), 0)
                """, UUID.randomUUID(), image);
        jdbc.update("""
                INSERT INTO portfolio.active_cv_documents
                    (id, active_cv_profile_id, language, asset_id, selected_at, created_at, updated_at)
                VALUES (?, '00000000-0000-0000-0000-000000000801', 'EN', ?, now(), now(), now())
                """, UUID.randomUUID(), pdf);

        Path root = temporaryStorage.resolve("media");
        MediaStorageReadinessService readiness = new MediaStorageReadinessService(root);
        readiness.verifyReady();
        Files.write(root.resolve("original").resolve(image.toString()), imageBytes);
        Files.write(root.resolve("original").resolve(pdf.toString()), pdfBytes);
        Files.write(root.resolve("variants").resolve(variant.toString()), variantBytes);
        var mediaService = new MediaRecoveryConsistencyService(
                new JdbcMediaRecoveryInventoryReadAdapter(provider()), new LocalFileAssetStorageAdapter(root));
        @SuppressWarnings("unchecked")
        ObjectProvider<MediaRecoveryConsistencyService> mediaProvider = mock(ObjectProvider.class);
        when(mediaProvider.getIfAvailable()).thenReturn(mediaService);
        OperationsHealthQueryPort health = () -> new OperationsHealthSnapshot(
                OperationsComponentStatus.UP, OperationsComponentStatus.UP, OperationsComponentStatus.DOWN,
                OperationsComponentStatus.UP, CachePurgeProvider.LOCAL, OperationsComponentStatus.UP,
                new MigrationStatusSummary("21", "21", 0, MigrationStatus.UP_TO_DATE));
        var coordinator = new RecoveryVerificationCoordinator(
                () -> new ApplicationReleaseInfo("persefonia", "0.1.0-SNAPSHOT"), health, mediaProvider,
                new JdbcDurableAssetReferenceIntegrityReadAdapter(provider()),
                Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC));

        RecoveryVerificationReport report = coordinator.verify();

        assertThat(report.status()).isEqualTo(RecoveryVerificationStatus.CONSISTENT);
        assertThat(report.media().totalObjects()).isEqualTo(3);
        assertThat(report.media().verifiedObjects()).isEqualTo(3);
        assertThat(report.assetReferences().totalReferences()).isEqualTo(3);
        assertThat(report.assetReferences().danglingReferences()).isZero();
    }

    @Test
    void malformedPersistedMediaMetadataFailsClosedWithoutReturningUnsafeDetail() {
        insertAsset(UUID.randomUUID(), "PRIVATE", "NOT_REQUIRED", "not-a-sha256");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new JdbcMediaRecoveryInventoryReadAdapter(provider()).readPage(null, 200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("original/");
    }

    @Test
    void durableReferenceIssueDetailsAreCappedWithoutTruncatingCounts() {
        UUID missing = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        for (int index = 0; index < 105; index++) {
            jdbc.update("""
                    INSERT INTO portfolio.projects
                        (id, status, visibility, featured, cover_asset_id, created_at, updated_at, version)
                    VALUES (?, 'ACTIVE', 'PRIVATE', false, ?, now(), now(), 0)
                    """, UUID.randomUUID(), missing);
        }

        DurableAssetReferenceIntegritySummary summary =
                new JdbcDurableAssetReferenceIntegrityReadAdapter(provider()).verify();

        assertThat(summary.danglingReferences()).isEqualTo(105);
        assertThat(summary.reportedIssues()).hasSize(100);
        assertThat(summary.reportedIssuesTruncated()).isTrue();
    }

    private static List<MediaRecoveryObjectReference> traverse(JdbcMediaRecoveryInventoryReadAdapter adapter) {
        List<MediaRecoveryObjectReference> all = new ArrayList<>();
        MediaRecoveryCursor cursor = null;
        do {
            MediaRecoveryInventoryPage page = adapter.readPage(cursor, 200);
            all.addAll(page.items());
            cursor = page.nextCursor();
        } while (cursor != null);
        return all;
    }

    private static void insertAsset(UUID id, String visibility, String status, String checksum) {
        jdbc.update("""
                INSERT INTO media.assets
                    (id, original_filename, stored_filename, storage_path, content_type, file_extension,
                     size_bytes, checksum, kind, visibility, decorative, processing_status,
                     created_at, updated_at, version)
                VALUES (?, 'asset.bin', 'asset.bin', ?, 'application/octet-stream', 'bin',
                        1, ?, 'DOCUMENT', ?, false, ?, now(), now(), 0)
                """, id, "original/" + id, checksum, visibility, status);
    }

    private static void insertVariant(UUID id, UUID assetId, String name, String checksum) {
        jdbc.update("""
                INSERT INTO media.asset_variants
                    (id, asset_id, name, width, height, content_type, size_bytes, storage_path, checksum, created_at)
                VALUES (?, ?, ?, 1, 1, 'image/webp', 1, ?, ?, now())
                """, id, assetId, name, "variants/" + id, checksum);
    }

    private static void insertRecoverableAsset(
            UUID id, String kind, String visibility, String status, byte[] content, boolean image) throws Exception {
        jdbc.update("""
                INSERT INTO media.assets
                    (id, original_filename, stored_filename, storage_path, content_type, file_extension,
                     size_bytes, checksum, kind, visibility, image_width, image_height, decorative,
                     processing_status, created_at, updated_at, version)
                VALUES (?, 'asset.bin', 'asset.bin', ?, 'application/octet-stream', 'bin',
                        ?, ?, ?, ?, ?, ?, false, ?, now(), now(), 0)
                """, id, "original/" + id, content.length, checksum(content), kind, visibility,
                image ? 1 : null, image ? 1 : null, status);
    }

    private static String checksum(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<NamedParameterJdbcTemplate> provider() {
        ObjectProvider<NamedParameterJdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(namedJdbc);
        return provider;
    }
}
