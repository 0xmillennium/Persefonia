package dev.persefonia.app.transaction;

import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class SpringTransactionSynchronizationPostCommitTaskExecutor implements PostCommitTaskExecutor {
    @Override
    public void afterCommit(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }
}
