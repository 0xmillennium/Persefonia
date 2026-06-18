package dev.persefonia.medialibrary.application.storage;

import dev.persefonia.medialibrary.domain.asset.StoragePath;
import java.util.Arrays;
import java.util.Objects;

public record VariantStorageRequest(StoragePath storagePath, byte[] content) {
    public VariantStorageRequest {
        Objects.requireNonNull(storagePath, "storagePath");
        Objects.requireNonNull(content, "content");
        if (content.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }
        content = Arrays.copyOf(content, content.length);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
