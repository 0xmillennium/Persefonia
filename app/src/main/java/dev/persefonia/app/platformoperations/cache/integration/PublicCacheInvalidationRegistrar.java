package dev.persefonia.app.platformoperations.cache.integration;

import dev.persefonia.app.transaction.PostCommitTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PublicCacheInvalidationRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublicCacheInvalidationRegistrar.class);

    private final PostCommitTaskExecutor postCommitTasks;
    private final PublicCacheInvalidationCoordinator coordinator;

    private PublicCacheInvalidationRegistrar() {
        this.postCommitTasks = null;
        this.coordinator = null;
    }

    public static PublicCacheInvalidationRegistrar noOp() {
        return new PublicCacheInvalidationRegistrar();
    }

    public PublicCacheInvalidationRegistrar(
            PostCommitTaskExecutor postCommitTasks,
            PublicCacheInvalidationCoordinator coordinator) {
        this.postCommitTasks = postCommitTasks;
        this.coordinator = coordinator;
    }

    public void register(PublicCacheInvalidationSignal signal) {
        if (postCommitTasks == null) return;
        try {
            postCommitTasks.afterCommit(() -> handleSafely(signal));
        } catch (RuntimeException failure) {
            log(signal, "registration", failure);
        }
    }

    private void handleSafely(PublicCacheInvalidationSignal signal) {
        try {
            coordinator.handle(signal);
        } catch (RuntimeException failure) {
            log(signal, "after-commit", failure);
        }
    }

    private static void log(PublicCacheInvalidationSignal signal, String phase, RuntimeException failure) {
        LOGGER.warn("Public cache invalidation failed: kind={}, sourceId={}, phase={}, category={}",
                signal.kind(), signal.sourceId(), phase, failure.getClass().getSimpleName());
    }
}
