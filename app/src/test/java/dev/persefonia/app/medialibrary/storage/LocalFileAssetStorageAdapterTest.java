package dev.persefonia.app.medialibrary.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.medialibrary.application.storage.FinalAssetStorageKey;
import dev.persefonia.medialibrary.application.storage.OriginalAssetStagingRequest;
import dev.persefonia.medialibrary.application.storage.StagedAssetObject;
import dev.persefonia.medialibrary.application.storage.StorageWriteException;
import dev.persefonia.medialibrary.application.storage.StoredAssetObject;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileAssetStorageAdapterTest {
    @TempDir
    Path tempDirectory;

    @Test
    void stagesUnderRootAndCommitsToLogicalOriginalPath() throws Exception {
        LocalFileAssetStorageAdapter adapter = new LocalFileAssetStorageAdapter(tempDirectory);
        byte[] content = "media".getBytes();

        StagedAssetObject staged = adapter.stageOriginal(new OriginalAssetStagingRequest(
                () -> new ByteArrayInputStream(content), 100));
        Path stagedPath = tempDirectory.resolve(".staging").resolve(staged.stagingKey() + ".tmp");
        assertThat(staged.sizeBytes()).isEqualTo(content.length);
        assertThat(stagedPath).exists();

        String logicalPath = "original/asset-id/checksum.png";
        StoredAssetObject stored =
                adapter.commitStaged(staged, new FinalAssetStorageKey(logicalPath));

        assertThat(stored.logicalPath()).isEqualTo(logicalPath).doesNotStartWith(tempDirectory.toString());
        assertThat(tempDirectory.resolve(logicalPath)).hasBinaryContent(content);
        assertThat(stagedPath).doesNotExist();
    }

    @Test
    void rejectsTraversalAbsoluteAndNormalizedRootEscapePaths() {
        LocalFileAssetStorageAdapter adapter = new LocalFileAssetStorageAdapter(tempDirectory);
        StagedAssetObject staged = new StagedAssetObject("00000000-0000-0000-0000-000000000000", 1);

        for (String path : new String[] {
                "../escape.png",
                "original/../../escape.png",
                tempDirectory.resolve("absolute.png").toAbsolutePath().toString()
        }) {
            assertThatThrownBy(() -> adapter.commitStaged(staged, new FinalAssetStorageKey(path)))
                    .isInstanceOf(StorageWriteException.class);
        }
    }

    @Test
    void cleanupOperationsAreBestEffort() {
        LocalFileAssetStorageAdapter adapter = new LocalFileAssetStorageAdapter(tempDirectory);

        assertThatCode(() -> adapter.deleteStagedIfExists(
                new StagedAssetObject("not-a-valid-key", 0))).doesNotThrowAnyException();
        assertThatCode(() -> adapter.deleteStoredIfExists(
                new StoredAssetObject("../invalid"))).doesNotThrowAnyException();
    }

    @Test
    void doesNotUseOriginalFilenameAndDoesNotOverwriteExistingFile() throws Exception {
        LocalFileAssetStorageAdapter adapter = new LocalFileAssetStorageAdapter(tempDirectory);
        String logicalPath = "original/asset-id/checksum.jpg";
        Path finalPath = tempDirectory.resolve(logicalPath);
        Files.createDirectories(finalPath.getParent());
        Files.writeString(finalPath, "existing");
        StagedAssetObject staged = adapter.stageOriginal(new OriginalAssetStagingRequest(
                () -> new ByteArrayInputStream("new".getBytes()), 100));

        assertThatThrownBy(() -> adapter.commitStaged(staged, new FinalAssetStorageKey(logicalPath)))
                .isInstanceOf(StorageWriteException.class);
        assertThat(finalPath).hasContent("existing");
        assertThat(logicalPath).doesNotContain("my holiday photo.jpg");
    }

    @Test
    void stagingIsBoundedByTheRequestedLimit() {
        LocalFileAssetStorageAdapter adapter = new LocalFileAssetStorageAdapter(tempDirectory);

        StagedAssetObject staged = adapter.stageOriginal(new OriginalAssetStagingRequest(
                () -> new ByteArrayInputStream("1234567890".getBytes()), 4));

        assertThat(staged.sizeBytes()).isEqualTo(4);
    }
}
