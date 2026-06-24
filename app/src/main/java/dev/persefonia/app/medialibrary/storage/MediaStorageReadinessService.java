package dev.persefonia.app.medialibrary.storage;

import dev.persefonia.medialibrary.application.storage.StorageWriteException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class MediaStorageReadinessService {
    private final Path storageRoot;
    private final Path stagingRoot;
    private final Path originalRoot;
    private final Path variantRoot;

    public MediaStorageReadinessService(Path storageRoot) {
        this.storageRoot = Objects.requireNonNull(storageRoot, "storageRoot")
                .toAbsolutePath()
                .normalize();
        this.stagingRoot = this.storageRoot.resolve(".staging").normalize();
        this.originalRoot = this.storageRoot.resolve("original").normalize();
        this.variantRoot = this.storageRoot.resolve("variants").normalize();
    }

    public void verifyReady() {
        try {
            if (Files.exists(storageRoot) && !Files.isDirectory(storageRoot)) {
                throw new StorageWriteException("persefonia.media.storage-root must be a directory.");
            }
            Files.createDirectories(storageRoot);
            if (!Files.isWritable(storageRoot)) {
                throw new StorageWriteException("persefonia.media.storage-root must be writable.");
            }
            Files.createDirectories(stagingRoot);
            requireUnderRoot(stagingRoot, "Media staging path escapes persefonia.media.storage-root.");
            requireUnderRoot(originalRoot, "Media original path escapes persefonia.media.storage-root.");
            requireUnderRoot(variantRoot, "Media variant path escapes persefonia.media.storage-root.");
        } catch (IOException exception) {
            throw new StorageWriteException("Unable to verify persefonia.media.storage-root readiness.", exception);
        }
    }

    public Path storageRoot() {
        return storageRoot;
    }

    Path stagingRoot() {
        return stagingRoot;
    }

    Path originalRoot() {
        return originalRoot;
    }

    Path variantRoot() {
        return variantRoot;
    }

    private void requireUnderRoot(Path path, String message) {
        if (!path.startsWith(storageRoot) || path.equals(storageRoot)) {
            throw new StorageWriteException(message);
        }
    }
}
