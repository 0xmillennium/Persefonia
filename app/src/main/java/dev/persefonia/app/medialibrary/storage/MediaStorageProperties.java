package dev.persefonia.app.medialibrary.storage;

import dev.persefonia.medialibrary.application.upload.UploadValidationPolicy;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "persefonia.media")
public class MediaStorageProperties {
    private String storageRoot;
    private long maxImageBytes = UploadValidationPolicy.DEFAULT_MAX_IMAGE_BYTES;
    private long maxPdfBytes = UploadValidationPolicy.DEFAULT_MAX_PDF_BYTES;
    private long maxImagePixels = 40_000_000L;

    public String getStorageRoot() {
        return storageRoot;
    }

    Path requireStorageRootPath() {
        if (storageRoot == null || storageRoot.isBlank()) {
            throw new IllegalStateException("persefonia.media.storage-root must be configured.");
        }
        return Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        if (maxImageBytes <= 0) {
            throw new IllegalArgumentException("maxImageBytes must be positive");
        }
        this.maxImageBytes = maxImageBytes;
    }

    public long getMaxPdfBytes() {
        return maxPdfBytes;
    }

    public void setMaxPdfBytes(long maxPdfBytes) {
        if (maxPdfBytes <= 0) {
            throw new IllegalArgumentException("maxPdfBytes must be positive");
        }
        this.maxPdfBytes = maxPdfBytes;
    }

    public long getMaxImagePixels() {
        return maxImagePixels;
    }

    public void setMaxImagePixels(long maxImagePixels) {
        if (maxImagePixels <= 0) {
            throw new IllegalArgumentException("maxImagePixels must be positive");
        }
        this.maxImagePixels = maxImagePixels;
    }
}
