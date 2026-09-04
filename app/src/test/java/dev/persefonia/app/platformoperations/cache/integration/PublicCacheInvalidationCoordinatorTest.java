package dev.persefonia.app.platformoperations.cache.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.publicview.ContentPublicExposureSnapshot;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicMutationFacts;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicSurfaceDependencies;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationExecutionPort;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequest;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.taxonomy.application.discovery.TagPublicRouteFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PublicCacheInvalidationCoordinatorTest {
    private final CapturingExecution execution = new CapturingExecution();

    @Test
    void contentPlanIsCompleteDeduplicatedAndOneBatch() {
        var coordinator = coordinator(new ContentPublicSurfaceDependencies(
                List.of(new PublicUrl("/tr/tags/java")),
                List.of(new PublicUrl("/tr/series/architecture")),
                List.of(new PublicUrl("/tr/articles/current")), false));
        var listed = new ContentPublicExposureSnapshot(true, true, true, true);
        var facts = new ContentPublicMutationFacts(
                ContentId.newId(), listed, listed,
                Optional.of(new PublicUrl("/tr/articles/old")),
                Optional.of(new PublicUrl("/tr/articles/current")));

        coordinator.handle(new PublicCacheInvalidationSignal.ContentChanged(facts));

        assertThat(execution.requests).singleElement().satisfies(request ->
                assertThat(request.targets()).extracting(target -> target.value()).containsExactly(
                        "/feed.xml", "/sitemap.xml", "/tr/articles/current", "/tr/articles/old",
                        "/tr/series/architecture", "/tr/tags/java"));
    }

    @Test
    void draftLikeContentCreatesNoBatchAndDependencyFailureCreatesNoPartialBatch() {
        var none = ContentPublicExposureSnapshot.none();
        var facts = new ContentPublicMutationFacts(
                ContentId.newId(), none, none, Optional.empty(), Optional.empty());
        coordinator(new ContentPublicSurfaceDependencies(List.of(), List.of(), List.of(), false))
                .handle(new PublicCacheInvalidationSignal.ContentChanged(facts));
        assertThat(execution.requests).isEmpty();

        var listed = new ContentPublicExposureSnapshot(true, true, true, true);
        var publicFacts = new ContentPublicMutationFacts(
                ContentId.newId(), listed, listed, Optional.of(new PublicUrl("/en/notes/a")),
                Optional.of(new PublicUrl("/en/notes/a")));
        try {
            coordinatorThrowingDependencies().handle(new PublicCacheInvalidationSignal.ContentChanged(publicFacts));
        } catch (IllegalStateException expected) {
            // Registrar owns containment; coordinator must not execute a partial plan.
        }
        assertThat(execution.requests).isEmpty();
    }

    private PublicCacheInvalidationCoordinator coordinator(ContentPublicSurfaceDependencies dependencies) {
        return new PublicCacheInvalidationCoordinator(
                (id, limit) -> dependencies,
                (id, limit) -> List.of(),
                (ids, language, limit) -> List.of(),
                (id, limit) -> List.of(),
                (id, limit) -> List.of(),
                execution,
                new PublicCacheTargetPlanner(),
                new TagPublicRouteFactory(),
                new ProjectPublicRouteFactory());
    }

    private PublicCacheInvalidationCoordinator coordinatorThrowingDependencies() {
        return new PublicCacheInvalidationCoordinator(
                (id, limit) -> { throw new IllegalStateException("dependency unavailable"); },
                (id, limit) -> List.of(),
                (ids, language, limit) -> List.of(),
                (id, limit) -> List.of(),
                (id, limit) -> List.of(),
                execution,
                new PublicCacheTargetPlanner(),
                new TagPublicRouteFactory(),
                new ProjectPublicRouteFactory());
    }

    private static final class CapturingExecution implements CacheInvalidationExecutionPort {
        private final List<CacheInvalidationRequest> requests = new ArrayList<>();
        @Override public void requestAndExecute(CacheInvalidationRequest request) { requests.add(request); }
        @Override public void executeInitial(CacheInvalidationBatchId batchId) { }
        @Override public void executeManualRetry(CacheInvalidationBatchId batchId) { }
    }
}
