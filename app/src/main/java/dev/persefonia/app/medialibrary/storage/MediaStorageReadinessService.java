package dev.persefonia.app.medialibrary.storage;

import dev.persefonia.medialibrary.application.storage.StorageWriteException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;

public final class MediaStorageReadinessService {
    private static final byte[] PROBE_CONTENT = new byte[] {0x50, 0x52, 0x4f, 0x42, 0x45};
    private final Path storageRoot;
    private final Path stagingRoot;
    private final Path originalRoot;
    private final Path variantRoot;
    private final ReadWriteProbe readWriteProbe;

    public MediaStorageReadinessService(Path storageRoot) {
        this(storageRoot, MediaStorageReadinessService::verifyReadWrite);
    }

    MediaStorageReadinessService(Path storageRoot, ReadWriteProbe readWriteProbe) {
        this.storageRoot = Objects.requireNonNull(storageRoot, "storageRoot")
                .toAbsolutePath()
                .normalize();
        this.stagingRoot = this.storageRoot.resolve(".staging").normalize();
        this.originalRoot = this.storageRoot.resolve("original").normalize();
        this.variantRoot = this.storageRoot.resolve("variants").normalize();
        this.readWriteProbe = Objects.requireNonNull(readWriteProbe, "readWriteProbe");
    }

    public void verifyReady() {
        try {
            if (Files.exists(storageRoot) && !Files.isDirectory(storageRoot)) {
                throw new StorageWriteException("persefonia.media.storage-root must be a directory.");
            }
            Files.createDirectories(storageRoot);
            Files.createDirectories(stagingRoot);
            Files.createDirectories(originalRoot);
            Files.createDirectories(variantRoot);
            requireUnderRoot(stagingRoot, "Media staging path escapes persefonia.media.storage-root.");
            requireUnderRoot(originalRoot, "Media original path escapes persefonia.media.storage-root.");
            requireUnderRoot(variantRoot, "Media variant path escapes persefonia.media.storage-root.");
            requireUsableDirectory(storageRoot, "Media storage root is unavailable.");
            requireUsableDirectory(stagingRoot, "Media staging directory is unavailable.");
            requireUsableDirectory(originalRoot, "Media original directory is unavailable.");
            requireUsableDirectory(variantRoot, "Media variants directory is unavailable.");
            Path realRoot = storageRoot.toRealPath();
            requireRealPathUnderRoot(stagingRoot, realRoot);
            requireRealPathUnderRoot(originalRoot, realRoot);
            requireRealPathUnderRoot(variantRoot, realRoot);

            readWriteProbe.verify(stagingRoot);
        } catch (IOException exception) {
            throw new StorageWriteException("Unable to verify persefonia.media.storage-root readiness.");
        }
    }

    public boolean isRuntimeReady() {
        try {
            Path realRoot = storageRoot.toRealPath();
            requireUsableDirectory(storageRoot, "Media storage root is unavailable.");
            for (Path child : java.util.List.of(stagingRoot, originalRoot, variantRoot)) {
                requireUsableDirectory(child, "Media storage directory is unavailable.");
                requireRealPathUnderRoot(child, realRoot);
            }
            return true;
        } catch (RuntimeException | IOException failure) {
            return false;
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

    private static void requireUsableDirectory(Path path, String message) {
        if (!Files.isDirectory(path) || !Files.isReadable(path) || !Files.isWritable(path)) {
            throw new StorageWriteException(message);
        }
    }

    private static void requireRealPathUnderRoot(Path path, Path realRoot) throws IOException {
        if (!path.toRealPath().startsWith(realRoot) || path.toRealPath().equals(realRoot)) {
            throw new StorageWriteException("Media storage directory escapes the configured root.");
        }
    }

    private static void verifyReadWrite(Path stagingRoot) throws IOException {
        Path probe = null;
        try {
            probe = Files.createTempFile(stagingRoot, "readiness-", ".probe");
            Files.write(probe, PROBE_CONTENT, StandardOpenOption.TRUNCATE_EXISTING);
            byte[] actual = new byte[PROBE_CONTENT.length + 1];
            int read;
            try (var input = Files.newInputStream(probe, StandardOpenOption.READ)) {
                read = input.read(actual);
                if (read >= 0 && input.read() != -1) read++;
            }
            if (read != PROBE_CONTENT.length
                    || !Arrays.equals(PROBE_CONTENT, Arrays.copyOf(actual, PROBE_CONTENT.length))) {
                throw new IOException("probe content mismatch");
            }
        } finally {
            if (probe != null) {
                try { Files.deleteIfExists(probe); }
                catch (IOException | RuntimeException ignored) { /* best-effort readiness cleanup */ }
            }
        }
    }

    @FunctionalInterface
    interface ReadWriteProbe {
        void verify(Path stagingRoot) throws IOException;
    }
}
