package dev.persefonia.app.medialibrary.storage;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.upload.UploadAssetCommandService;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.Checksum;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MediaStorageConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MediaStorageConfiguration.class)
            .withBean(AssetRepository.class, NoOpAssetRepository::new);

    @Test
    void storageCompositionIsAbsentWhenRootIsNotConfigured() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AssetStoragePort.class);
            assertThat(context).doesNotHaveBean(UploadAssetCommandService.class);
        });
    }

    @Test
    void storageCompositionIsCreatedWhenRootIsConfigured() {
        contextRunner
                .withPropertyValues(
                        "persefonia.media.storage-root=" + System.getProperty("java.io.tmpdir") + "/persefonia-media-test",
                        "persefonia.media.max-image-bytes=1024",
                        "persefonia.media.max-pdf-bytes=2048")
                .run(context -> {
                    assertThat(context).hasSingleBean(AssetStoragePort.class);
                    assertThat(context).hasSingleBean(UploadAssetCommandService.class);
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
