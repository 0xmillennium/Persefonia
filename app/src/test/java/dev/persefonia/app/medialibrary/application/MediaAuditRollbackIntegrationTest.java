package dev.persefonia.app.medialibrary.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.medialibrary.application.admin.AdminUploadAssetCommand;
import dev.persefonia.medialibrary.application.admin.AdminUploadAssetResult;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandGateway;
import dev.persefonia.medialibrary.application.authorization.MediaCommandActor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
class MediaAuditRollbackIntegrationTest {
    private static final byte[] PNG = createPng();
    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final PostgreSQLContainer POSTGRES = postgresContainer();

    static {
        POSTGRES.start();
    }

    @Autowired MediaAdminCommandGateway gateway;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("persefonia.media.storage-root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void reset() throws IOException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas("operations")
                .createSchemas(true)
                .load()
                .migrate();
        jdbc.execute("TRUNCATE media.assets, audit.audit_records CASCADE");
        clearStorageRoot();
    }

    @Test
    void auditFailureRollsBackAssetAndRemovesOriginalAndVariants() {
        MediaCommandActor owner = new MediaCommandActor(UUID.randomUUID(), true, true);
        AdminUploadAssetCommand command = new AdminUploadAssetCommand(
                owner, "private.png", "image/png", "png", PNG.length,
                () -> new ByteArrayInputStream(PNG));

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    jdbc.execute("""
                            ALTER TABLE audit.audit_records
                            ADD CONSTRAINT reject_media_audit_test CHECK (false)
                            """);
                    gateway.upload(command);
                }))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM media.assets", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media.asset_variants", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_records", Long.class)).isZero();
        assertThat(storedRegularFiles()).isEmpty();
    }

    @Test
    void successfulUploadCommitsAssetFilesAndExactlyOneAuditRecord() {
        MediaCommandActor owner = new MediaCommandActor(UUID.randomUUID(), true, true);
        AdminUploadAssetCommand command = new AdminUploadAssetCommand(
                owner, "private.png", "image/png", "png", PNG.length,
                () -> new ByteArrayInputStream(PNG));

        assertThat(gateway.upload(command)).isInstanceOf(AdminUploadAssetResult.Created.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM media.assets", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media.asset_variants", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_records WHERE action = 'asset.uploaded'", Long.class))
                .isEqualTo(1);
        assertThat(storedRegularFiles()).isNotEmpty();
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("persefonia-media-audit-rollback-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static byte[] createPng() {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336699);
        try (var output = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void clearStorageRoot() throws IOException {
        try (var paths = Files.walk(STORAGE_ROOT)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(STORAGE_ROOT))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
        Files.createDirectories(STORAGE_ROOT.resolve(".staging"));
    }

    private static java.util.List<Path> storedRegularFiles() {
        try (var paths = Files.walk(STORAGE_ROOT)) {
            return paths.filter(Files::isRegularFile).toList();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static PostgreSQLContainer postgresContainer() {
        PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
        postgres.withDatabaseName("persefonia_media_audit_rollback");
        postgres.withUsername("persefonia");
        postgres.withPassword("persefonia_dev");
        return postgres;
    }
}
