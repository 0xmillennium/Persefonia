package dev.persefonia.platformoperations.application.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatch;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchRepository;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationStatus;
import dev.persefonia.platformoperations.domain.cache.CacheTargetType;
import dev.persefonia.platformoperations.domain.cache.InvalidationReason;
import dev.persefonia.platformoperations.domain.cache.InvalidationRequester;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CacheInvalidationRequestServiceTest {
    @Test
    void validatesDeduplicatesAndPersistsOneRequestedBatchUsingClock() {
        Instant now = Instant.parse("2026-09-03T12:00:00Z");
        RecordingRepository repository = new RecordingRepository();
        CacheInvalidationRequestService service = new CacheInvalidationRequestService(
                repository, Clock.fixed(now, ZoneOffset.UTC));

        CacheInvalidationBatchId id = service.request(new CacheInvalidationRequest(
                InvalidationReason.PUBLIC_RESOURCE_CHANGED, InvalidationRequester.SYSTEM,
                List.of(new CacheInvalidationTargetRequest(CacheTargetType.URL, "/articles/example"),
                        new CacheInvalidationTargetRequest(CacheTargetType.URL, "/articles/example"),
                        new CacheInvalidationTargetRequest(CacheTargetType.CACHE_TAG, "site:public-documents"))));

        assertThat(repository.saved.id()).isEqualTo(id);
        assertThat(repository.saved.requestedAt()).isEqualTo(now);
        assertThat(repository.saved.status()).isEqualTo(CacheInvalidationStatus.REQUESTED);
        assertThat(repository.saved.targets()).hasSize(2);
        assertThat(repository.saved.attempts()).isEmpty();
    }

    private static final class RecordingRepository implements CacheInvalidationBatchRepository {
        private CacheInvalidationBatch saved;
        @Override public void save(CacheInvalidationBatch batch) { saved = batch; }
        @Override public Optional<CacheInvalidationBatch> findById(CacheInvalidationBatchId id) { return Optional.empty(); }
        @Override public List<CacheInvalidationBatch> findPendingBatches(int limit) { return List.of(); }
        @Override public List<CacheInvalidationBatch> findRecentFailures(int limit) { return List.of(); }
    }
}
