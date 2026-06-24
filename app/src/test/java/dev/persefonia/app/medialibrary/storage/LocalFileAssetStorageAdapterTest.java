package dev.persefonia.app.medialibrary.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.medialibrary.application.storage.FinalAssetStorageKey;
import dev.persefonia.medialibrary.application.storage.OriginalAssetStagingRequest;
import dev.persefonia.medialibrary.application.storage.StagedAssetObject;
import dev.persefonia.medialibrary.application.storage.StorageWriteException;
import dev.persefonia.medialibrary.application.storage.StoredAssetObject;
import dev.persefonia.medialibrary.application.storage.VariantStorageRequest;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        assertThat(stagedPath(staged)).doesNotExist();
        assertThat(logicalPath).doesNotContain("my holiday photo.jpg");
    }

    @Test
    void failedOriginalCommitCleansStagedFileAndLeavesNoEmptyFinalFile() throws Exception {
        LocalFileAssetStorageAdapter adapter = new LocalFileAssetStorageAdapter(tempDirectory);
        Path parentAsFile = tempDirectory.resolve("original/asset-id");
        Files.createDirectories(parentAsFile.getParent());
        Files.writeString(parentAsFile, "not a directory");
        StagedAssetObject staged = adapter.stageOriginal(new OriginalAssetStagingRequest(
                () -> new ByteArrayInputStream("new".getBytes()), 100));
        Path finalPath = parentAsFile.resolve("checksum.jpg");

        assertThatThrownBy(() -> adapter.commitStaged(
                        staged, new FinalAssetStorageKey("original/asset-id/checksum.jpg")))
                .isInstanceOf(StorageWriteException.class);

        assertThat(stagedPath(staged)).doesNotExist();
        assertThat(finalPath).doesNotExist();
    }

    @Test
    void stagingIsBoundedByTheRequestedLimit() {
        LocalFileAssetStorageAdapter adapter = new LocalFileAssetStorageAdapter(tempDirectory);

        StagedAssetObject staged = adapter.stageOriginal(new OriginalAssetStagingRequest(
                () -> new ByteArrayInputStream("1234567890".getBytes()), 4));

        assertThat(staged.sizeBytes()).isEqualTo(4);
    }

    @Test
    void opensStoredOriginalAndStoresAndDeletesVariantByLogicalPath() throws Exception {
        LocalFileAssetStorageAdapter adapter = new LocalFileAssetStorageAdapter(tempDirectory);
        byte[] original = "original".getBytes();
        StagedAssetObject staged = adapter.stageOriginal(new OriginalAssetStagingRequest(
                () -> new ByteArrayInputStream(original), 100));
        StoredAssetObject stored = adapter.commitStaged(
                staged, new FinalAssetStorageKey("original/asset/checksum.png"));

        try (var input = adapter.openStored(StoragePath.of(stored.logicalPath()))) {
            assertThat(input.readAllBytes()).isEqualTo(original);
        }

        StoragePath variantPath = StoragePath.of("variants/asset/thumbnail-checksum.png");
        StoredAssetObject variant = adapter.storeVariant(
                new VariantStorageRequest(variantPath, "variant".getBytes()));
        assertThat(variant.logicalPath()).isEqualTo(variantPath.value());
        assertThat(tempDirectory.resolve(variantPath.value())).hasBinaryContent("variant".getBytes());

        adapter.deleteStoredByPathIfExists(variantPath);
        assertThat(tempDirectory.resolve(variantPath.value())).doesNotExist();
    }

    @Test
    void variantStorageDoesNotOverwriteExistingFinalFileAndCleansTemporaryFile() throws Exception {
        LocalFileAssetStorageAdapter adapter = new LocalFileAssetStorageAdapter(tempDirectory);
        StoragePath variantPath = StoragePath.of("variants/asset/thumbnail-checksum.png");
        Path finalPath = tempDirectory.resolve(variantPath.value());
        Files.createDirectories(finalPath.getParent());
        Files.writeString(finalPath, "existing");

        assertThatThrownBy(() -> adapter.storeVariant(new VariantStorageRequest(
                        variantPath, "replacement".getBytes())))
                .isInstanceOf(StorageWriteException.class);

        assertThat(finalPath).hasContent("existing");
        assertThat(stagingFiles()).isEmpty();
    }

    @Test
    void failedVariantWriteCleansTemporaryFileAndLeavesNoEmptyFinalFile() throws Exception {
        LocalFileAssetStorageAdapter adapter = new LocalFileAssetStorageAdapter(tempDirectory);
        Path parentAsFile = tempDirectory.resolve("variants/asset");
        Files.createDirectories(parentAsFile.getParent());
        Files.writeString(parentAsFile, "not a directory");
        Path finalPath = parentAsFile.resolve("thumbnail-checksum.png");

        assertThatThrownBy(() -> adapter.storeVariant(new VariantStorageRequest(
                        StoragePath.of("variants/asset/thumbnail-checksum.png"), "variant".getBytes())))
                .isInstanceOf(StorageWriteException.class);

        assertThat(finalPath).doesNotExist();
        assertThat(stagingFiles()).isEmpty();
    }

    @Test
    void variantStorageRejectsUnsafePathsAndMissingStoredObjectFailsSafely() {
        LocalFileAssetStorageAdapter adapter = new LocalFileAssetStorageAdapter(tempDirectory);

        for (String path : new String[] {"../escape.png", "/absolute.png", "variants/../../escape.png"}) {
            assertThatThrownBy(() -> adapter.storeVariant(new VariantStorageRequest(
                    new StoragePath(path), "variant".getBytes())))
                    .isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(() -> adapter.openStored(StoragePath.of("missing/file.png")))
                .isInstanceOf(StorageWriteException.class)
                .hasMessageNotContaining(tempDirectory.toString());
        assertThatThrownBy(() -> adapter.storeVariant(new VariantStorageRequest(
                StoragePath.of("original/asset/not-a-variant.png"), "variant".getBytes())))
                .isInstanceOf(StorageWriteException.class);
    }

    private Path stagedPath(StagedAssetObject staged) {
        return tempDirectory.resolve(".staging").resolve(staged.stagingKey() + ".tmp");
    }

    private List<Path> stagingFiles() throws Exception {
        try (var files = Files.list(tempDirectory.resolve(".staging"))) {
            return files.toList();
        }
    }
}
