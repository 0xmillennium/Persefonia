package dev.persefonia.app.medialibrary.storage;

import dev.persefonia.medialibrary.application.upload.UploadValidationPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "persefonia.media")
public class MediaStorageProperties {
    private String storageRoot;
    private long maxImageBytes = UploadValidationPolicy.DEFAULT_MAX_IMAGE_BYTES;
    private long maxPdfBytes = UploadValidationPolicy.DEFAULT_MAX_PDF_BYTES;

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        if (storageRoot == null || storageRoot.isBlank()) {
            throw new IllegalArgumentException("storageRoot must not be blank");
        }
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
}
