package dev.persefonia.app.medialibrary.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SpringAssetStorageRollbackCompensationAdapterTest {
    private static final StoragePath PATH = StoragePath.of("original/asset/file.pdf");

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void rollbackDeletesRegisteredPath() {
        AssetStoragePort storage = mock(AssetStoragePort.class);
        beginTransaction();
        new SpringAssetStorageRollbackCompensationAdapter(storage).deleteOnRollback(PATH);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).deleteStoredByPathIfExists(PATH);
    }

    @Test
    void commitRetainsRegisteredPath() {
        AssetStoragePort storage = mock(AssetStoragePort.class);
        beginTransaction();
        new SpringAssetStorageRollbackCompensationAdapter(storage).deleteOnRollback(PATH);

        complete(TransactionSynchronization.STATUS_COMMITTED);

        verify(storage, never()).deleteStoredByPathIfExists(PATH);
    }

    @Test
    void noTransactionRequiresNoCallback() {
        AssetStoragePort storage = mock(AssetStoragePort.class);

        new SpringAssetStorageRollbackCompensationAdapter(storage).deleteOnRollback(PATH);

        verify(storage, never()).deleteStoredByPathIfExists(PATH);
    }

    private static void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void complete(int status) {
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(status);
        }
    }
}
