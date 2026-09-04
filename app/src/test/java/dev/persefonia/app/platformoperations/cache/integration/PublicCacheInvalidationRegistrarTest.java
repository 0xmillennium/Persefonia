package dev.persefonia.app.platformoperations.cache.integration;

import static org.assertj.core.api.Assertions.assertThatCode;

import dev.persefonia.app.transaction.PostCommitTaskExecutor;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationExecutionPort;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequest;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.taxonomy.application.discovery.TagPublicRouteFactory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicCacheInvalidationRegistrarTest {
    @Test
    void registrationFailureIsFailOpen() {
        PublicCacheInvalidationRegistrar registrar = new PublicCacheInvalidationRegistrar(
                task -> { throw new IllegalStateException("registration failed"); }, coordinator(request -> { }));

        assertThatCode(() -> registrar.register(redirectSignal())).doesNotThrowAnyException();
    }

    @Test
    void callbackAndExecutionFailureIsContained() {
        RecordingPostCommit postCommit = new RecordingPostCommit();
        PublicCacheInvalidationRegistrar registrar = new PublicCacheInvalidationRegistrar(
                postCommit, coordinator(request -> { throw new IllegalStateException("execution failed"); }));
        registrar.register(redirectSignal());

        assertThatCode(postCommit.task::run).doesNotThrowAnyException();
    }

    private static PublicCacheInvalidationSignal redirectSignal() {
        return new PublicCacheInvalidationSignal.RedirectChanged(UUID.randomUUID(), new PublicUrl("/old"));
    }

    private static PublicCacheInvalidationCoordinator coordinator(java.util.function.Consumer<CacheInvalidationRequest> call) {
        CacheInvalidationExecutionPort execution = new CacheInvalidationExecutionPort() {
            @Override public void requestAndExecute(CacheInvalidationRequest request) { call.accept(request); }
            @Override public void executeInitial(CacheInvalidationBatchId batchId) { }
            @Override public void executeManualRetry(CacheInvalidationBatchId batchId) { }
            @Override public void resumeStranded(CacheInvalidationBatchId batchId) { }
        };
        return new PublicCacheInvalidationCoordinator(
                (id, limit) -> null, (id, limit) -> List.of(), (ids, language, limit) -> List.of(),
                (id, limit) -> List.of(), (id, limit) -> List.of(), execution,
                new PublicCacheTargetPlanner(), new TagPublicRouteFactory(), new ProjectPublicRouteFactory());
    }

    private static final class RecordingPostCommit implements PostCommitTaskExecutor {
        private Runnable task;
        @Override public void afterCommit(Runnable task) { this.task = task; }
    }
}
