package dev.persefonia.medialibrary.application.storage;

import dev.persefonia.medialibrary.domain.asset.StoragePath;

@FunctionalInterface
public interface AssetStorageRollbackCompensationPort {
    void deleteOnRollback(StoragePath path);

    static AssetStorageRollbackCompensationPort noOp() {
        return path -> {
        };
    }
}
