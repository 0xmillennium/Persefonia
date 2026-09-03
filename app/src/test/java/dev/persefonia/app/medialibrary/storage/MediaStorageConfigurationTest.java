package dev.persefonia.app.medialibrary.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.persefonia.app.audit.integration.MediaAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.authorization.MediaCommandAuthorizationPolicy;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.storage.StorageWriteException;
import dev.persefonia.medialibrary.application.upload.UploadAssetCommandService;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.Checksum;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MediaStorageConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MediaStorageConfiguration.class, MediaStorageRequiredConfiguration.class)
            .withBean(AssetRepository.class, NoOpAssetRepository::new)
            .withBean(MediaCommandAuthorizationPolicy.class, () -> mock(MediaCommandAuthorizationPolicy.class))
            .withBean(AppendAuditRecordPort.class, () -> mock(AppendAuditRecordPort.class))
            .withBean(MediaAuditMapper.class, () -> mock(MediaAuditMapper.class));

    @TempDir
    Path tempDirectory;

    @Test
    void storageCompositionIsAbsentWhenRootIsNotConfigured() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AssetStoragePort.class);
            assertThat(context).doesNotHaveBean(UploadAssetCommandService.class);
        });
    }

    @Test
    void storageCompositionIsCreatedWhenRootIsConfigured() {
        Path storageRoot = tempDirectory.resolve("media-root");

        contextRunner
                .withPropertyValues(
                        "persefonia.media.storage-root=" + storageRoot,
                        "persefonia.media.max-image-bytes=1024",
                        "persefonia.media.max-pdf-bytes=2048")
                .run(context -> {
                    assertThat(context).hasSingleBean(AssetStoragePort.class);
                    assertThat(context).hasSingleBean(MediaStorageReadinessService.class);
                    assertThat(context).hasSingleBean(UploadAssetCommandService.class);
                    assertThat(context.getBean(MediaStorageReadinessService.class).storageRoot())
                            .isEqualTo(storageRoot.toAbsolutePath().normalize());
                });
    }

    @Test
    void productionLikeStorageRequirementFailsWhenRootIsMissing() {
        contextRunner
                .withPropertyValues("persefonia.media.storage-required=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("persefonia.media.storage-root");
                });
    }

    @Test
    void blankStorageRootFailsWithActionablePropertyName() {
        contextRunner
                .withPropertyValues("persefonia.media.storage-root= ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("persefonia.media.storage-root");
                });
    }

    @Test
    void fileStorageRootFailsReadinessValidation() throws Exception {
        Path fileRoot = tempDirectory.resolve("not-a-directory");
        Files.writeString(fileRoot, "not a directory");

        contextRunner
                .withPropertyValues(
                        "persefonia.media.storage-required=true",
                        "persefonia.media.storage-root=" + fileRoot)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(StorageWriteException.class)
                            .hasMessageContaining("persefonia.media.storage-root");
                });
    }

    @Test
    void readinessPathsRemainConfinedToNormalizedRoot() {
        Path storageRoot = tempDirectory.resolve("nested").resolve("..").resolve("media-root");

        contextRunner
                .withPropertyValues("persefonia.media.storage-root=" + storageRoot)
                .run(context -> {
                    MediaStorageReadinessService readiness =
                            context.getBean(MediaStorageReadinessService.class);
                    Path normalizedRoot = storageRoot.toAbsolutePath().normalize();

                    assertThat(readiness.storageRoot()).isEqualTo(normalizedRoot);
                    assertThat(readiness.stagingRoot().startsWith(normalizedRoot)).isTrue();
                    assertThat(readiness.originalRoot().startsWith(normalizedRoot)).isTrue();
                    assertThat(readiness.variantRoot().startsWith(normalizedRoot)).isTrue();
                });
    }

    private static final class NoOpAssetRepository implements AssetRepository {
        @Override
        public Asset save(Asset asset) {
            return asset;
        }

        @Override
        public Optional<Asset> findById(AssetId id) {
            return Optional.empty();
        }

        @Override
        public Optional<Asset> findByChecksum(Checksum checksum) {
            return Optional.empty();
        }
    }
}
