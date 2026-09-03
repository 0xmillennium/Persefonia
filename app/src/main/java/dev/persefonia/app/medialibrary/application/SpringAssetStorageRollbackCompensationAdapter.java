package dev.persefonia.app.medialibrary.application;

import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.storage.AssetStorageRollbackCompensationPort;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class SpringAssetStorageRollbackCompensationAdapter
        implements AssetStorageRollbackCompensationPort {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(SpringAssetStorageRollbackCompensationAdapter.class);

    private final AssetStoragePort storage;

    public SpringAssetStorageRollbackCompensationAdapter(AssetStoragePort storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public void deleteOnRollback(StoragePath path) {
        Objects.requireNonNull(path, "path");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    storage.deleteStoredByPathIfExists(path);
                } catch (RuntimeException cleanupFailure) {
                    LOGGER.warn("Unable to remove newly-created media storage object after transaction rollback");
                }
            }
        });
    }
}
