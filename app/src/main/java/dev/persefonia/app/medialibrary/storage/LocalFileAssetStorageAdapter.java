package dev.persefonia.app.medialibrary.storage;

import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.storage.FinalAssetStorageKey;
import dev.persefonia.medialibrary.application.storage.OriginalAssetStagingRequest;
import dev.persefonia.medialibrary.application.storage.StagedAssetObject;
import dev.persefonia.medialibrary.application.storage.StorageWriteException;
import dev.persefonia.medialibrary.application.storage.StoredAssetObject;
import dev.persefonia.medialibrary.application.storage.VariantStorageRequest;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

public final class LocalFileAssetStorageAdapter implements AssetStoragePort {
    private static final int COPY_BUFFER_SIZE = 8192;

    private final Path storageRoot;
    private final Path realStorageRoot;
    private final Path stagingRoot;

    public LocalFileAssetStorageAdapter(Path storageRoot) {
        this.storageRoot = Objects.requireNonNull(storageRoot, "storageRoot")
                .toAbsolutePath()
                .normalize();
        this.stagingRoot = this.storageRoot.resolve(".staging").normalize();
        try {
            Files.createDirectories(this.stagingRoot);
            this.realStorageRoot = this.storageRoot.toRealPath();
            if (!this.stagingRoot.toRealPath().startsWith(this.realStorageRoot)) {
                throw new StorageWriteException("Media staging path escapes the storage root.");
            }
        } catch (IOException exception) {
            throw new StorageWriteException("Unable to initialize media storage.", exception);
        }
    }

    @Override
    public StagedAssetObject stageOriginal(OriginalAssetStagingRequest request) {
        Objects.requireNonNull(request, "request");
        String stagingKey = UUID.randomUUID().toString();
        Path stagedPath = stagedPath(stagingKey);
        long written = 0;
        try (InputStream input = request.byteSource().openStream();
                OutputStream output = Files.newOutputStream(
                        stagedPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            while (written < request.maximumBytesToStage()) {
                int requested = (int) Math.min(buffer.length, request.maximumBytesToStage() - written);
                int read = input.read(buffer, 0, requested);
                if (read == -1) {
                    break;
                }
                output.write(buffer, 0, read);
                written += read;
            }
            return new StagedAssetObject(stagingKey, written);
        } catch (IOException exception) {
            deleteBestEffort(stagedPath);
            throw new StorageWriteException("Unable to stage uploaded media.", exception);
        }
    }

    @Override
    public InputStream openStaged(StagedAssetObject stagedObject) {
        Objects.requireNonNull(stagedObject, "stagedObject");
        try {
            return Files.newInputStream(stagedPath(stagedObject.stagingKey()), StandardOpenOption.READ);
        } catch (IOException exception) {
            throw new StorageWriteException("Unable to open staged media.", exception);
        }
    }

    @Override
    public StoredAssetObject commitStaged(StagedAssetObject stagedObject, FinalAssetStorageKey finalKey) {
        Objects.requireNonNull(stagedObject, "stagedObject");
        Objects.requireNonNull(finalKey, "finalKey");
        Path stagedPath = stagedPath(stagedObject.stagingKey());
        try {
            Path finalPath = resolveLogicalPath(finalKey.value());
            moveStagedToFinal(stagedPath, finalPath);
            return new StoredAssetObject(finalKey.value());
        } catch (FileAlreadyExistsException exception) {
            deleteBestEffort(stagedPath);
            throw new StorageWriteException("Stored media already exists.", exception);
        } catch (IOException exception) {
            deleteBestEffort(stagedPath);
            throw new StorageWriteException("Unable to commit staged media.", exception);
        } catch (RuntimeException exception) {
            deleteBestEffort(stagedPath);
            throw exception;
        }
    }

    @Override
    public InputStream openStored(StoragePath storagePath) {
        Objects.requireNonNull(storagePath, "storagePath");
        try {
            Path storedPath = resolveLogicalPath(storagePath.value()).toRealPath();
            if (!storedPath.startsWith(realStorageRoot) || !Files.isRegularFile(storedPath)) {
                throw new StorageWriteException("Stored media path escapes the storage root.");
            }
            return Files.newInputStream(storedPath, StandardOpenOption.READ);
        } catch (IOException exception) {
            throw new StorageWriteException("Unable to open stored media.", exception);
        }
    }

    @Override
    public StoredAssetObject storeVariant(VariantStorageRequest request) {
        Objects.requireNonNull(request, "request");
        String logicalPath = request.storagePath().value();
        if (!logicalPath.startsWith("variants/")) {
            throw new StorageWriteException("Generated variant path must be under variants/.");
        }
        Path variantPath = resolveLogicalPath(logicalPath);
        Path stagedVariantPath = stagedPath(UUID.randomUUID().toString());
        try {
            try (OutputStream output = Files.newOutputStream(
                    stagedVariantPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                output.write(request.content());
            }
            moveStagedToFinal(stagedVariantPath, variantPath);
            return new StoredAssetObject(logicalPath);
        } catch (FileAlreadyExistsException exception) {
            deleteBestEffort(stagedVariantPath);
            throw new StorageWriteException("Stored media already exists.", exception);
        } catch (IOException exception) {
            deleteBestEffort(stagedVariantPath);
            throw new StorageWriteException("Unable to store generated media variant.", exception);
        } catch (RuntimeException exception) {
            deleteBestEffort(stagedVariantPath);
            throw exception;
        }
    }

    @Override
    public void deleteStagedIfExists(StagedAssetObject stagedObject) {
        if (stagedObject != null) {
            try {
                deleteBestEffort(stagedPath(stagedObject.stagingKey()));
            } catch (RuntimeException ignored) {
                // Compensating cleanup is deliberately best-effort.
            }
        }
    }

    @Override
    public void deleteStoredIfExists(StoredAssetObject storedObject) {
        if (storedObject != null) {
            try {
                deleteBestEffort(resolveLogicalPath(storedObject.logicalPath()));
            } catch (RuntimeException ignored) {
                // Compensating cleanup is deliberately best-effort.
            }
        }
    }

    @Override
    public void deleteStoredByPathIfExists(StoragePath storagePath) {
        if (storagePath != null) {
            try {
                deleteBestEffort(resolveLogicalPath(storagePath.value()));
            } catch (RuntimeException ignored) {
                // Compensating cleanup is deliberately best-effort.
            }
        }
    }

    private Path stagedPath(String stagingKey) {
        if (stagingKey == null || !stagingKey.matches("[0-9a-fA-F-]{36}")) {
            throw new StorageWriteException("Invalid staged media key.");
        }
        Path resolved = stagingRoot.resolve(stagingKey + ".tmp").normalize();
        if (!resolved.startsWith(stagingRoot)) {
            throw new StorageWriteException("Staged media path escapes the storage root.");
        }
        return resolved;
    }

    private Path resolveLogicalPath(String logicalPath) {
        if (logicalPath == null
                || logicalPath.isBlank()
                || logicalPath.indexOf('\0') >= 0
                || logicalPath.contains("\\")
                || logicalPath.contains(":")) {
            throw new StorageWriteException("Invalid logical media path.");
        }
        Path logical = Path.of(logicalPath);
        if (logical.isAbsolute()) {
            throw new StorageWriteException("Logical media path must be relative.");
        }
        for (Path segment : logical) {
            if (segment.toString().equals("..") || segment.toString().equals(".")) {
                throw new StorageWriteException("Logical media path contains traversal.");
            }
        }
        Path resolved = storageRoot.resolve(logical).normalize();
        if (!resolved.startsWith(storageRoot) || resolved.equals(storageRoot)) {
            throw new StorageWriteException("Logical media path escapes the storage root.");
        }
        return resolved;
    }

    private void moveStagedToFinal(Path stagedPath, Path finalPath) throws IOException {
        Files.createDirectories(finalPath.getParent());
        verifyExistingDirectoryWithinRoot(finalPath.getParent());
        if (Files.exists(finalPath)) {
            throw new FileAlreadyExistsException(finalPath.toString());
        }
        try {
            Files.move(stagedPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(stagedPath, finalPath);
        }
    }

    private void verifyExistingDirectoryWithinRoot(Path directory) {
        try {
            if (!directory.toRealPath().startsWith(realStorageRoot)) {
                throw new StorageWriteException("Logical media path escapes the storage root.");
            }
        } catch (IOException exception) {
            throw new StorageWriteException("Unable to verify logical media path.", exception);
        }
    }

    private static void deleteBestEffort(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException ignored) {
            // Compensating cleanup is deliberately best-effort.
        }
    }
}
