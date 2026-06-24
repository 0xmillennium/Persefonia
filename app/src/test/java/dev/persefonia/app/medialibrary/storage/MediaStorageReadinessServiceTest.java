package dev.persefonia.app.medialibrary.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.medialibrary.application.storage.StorageWriteException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaStorageReadinessServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void readinessPassesForValidStorageRootAndCreatesStagingDirectory() {
        Path root = tempDirectory.resolve("media");
        MediaStorageReadinessService readiness = new MediaStorageReadinessService(root);

        readiness.verifyReady();

        Path normalizedRoot = root.toAbsolutePath().normalize();
        assertThat(readiness.storageRoot()).isEqualTo(normalizedRoot);
        assertThat(readiness.stagingRoot()).isDirectory();
        assertThat(readiness.stagingRoot().startsWith(normalizedRoot)).isTrue();
        assertThat(readiness.originalRoot().startsWith(normalizedRoot)).isTrue();
        assertThat(readiness.variantRoot().startsWith(normalizedRoot)).isTrue();
    }

    @Test
    void readinessFailsForFileRootWithActionableReason() throws Exception {
        Path fileRoot = tempDirectory.resolve("media-file");
        Files.writeString(fileRoot, "not a directory");
        MediaStorageReadinessService readiness = new MediaStorageReadinessService(fileRoot);

        assertThatThrownBy(readiness::verifyReady)
                .isInstanceOf(StorageWriteException.class)
                .hasMessageContaining("persefonia.media.storage-root");
    }
}
