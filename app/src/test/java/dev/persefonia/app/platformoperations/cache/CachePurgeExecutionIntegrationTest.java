package dev.persefonia.app.platformoperations.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.persefonia.app.platformoperations.cache.execution.CachePurgeExecutionCoordinator;
import dev.persefonia.app.platformoperations.cache.execution.CachePurgeTransactionService;
import dev.persefonia.app.platformoperations.cache.execution.NonTransactionalCachePurgeInvoker;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationExecutionPort;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequest;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequestPort;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequestService;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationTargetRequest;
import dev.persefonia.platformoperations.application.cache.CachePurgePort;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderRequest;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderResult;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatch;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchRepository;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationStatus;
import dev.persefonia.platformoperations.domain.cache.CachePurgeFailureReason;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import dev.persefonia.platformoperations.domain.cache.CacheTargetOutcome;
import dev.persefonia.platformoperations.domain.cache.CacheTargetStatus;
import dev.persefonia.platformoperations.domain.cache.CacheTargetType;
import dev.persefonia.platformoperations.domain.cache.InvalidationReason;
import dev.persefonia.platformoperations.domain.cache.InvalidationRequester;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

class CachePurgeExecutionIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-04T10:00:00Z");
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static ProviderBehavior providerBehavior;
    private static CachePurgeProvider providerIdentity;

    private AnnotationConfigApplicationContext context;
    private FailingRepository repository;
    private FakeProvider provider;
    private CacheInvalidationExecutionPort execution;
    private CacheInvalidationRequestPort requests;

    @BeforeAll
    static void migrate() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .defaultSchema("operations").schemas("operations").createSchemas(true).load().migrate();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @BeforeEach
    void startContext() {
        jdbc.execute("TRUNCATE operations.cache_invalidation_batches CASCADE");
        providerBehavior = ProviderBehavior.SKIP_SUCCESS;
        providerIdentity = CachePurgeProvider.LOCAL;
        openContext();
    }

    @AfterEach
    void closeContext() {
        if (context != null) context.close();
    }

    @Test
    void commitsRunningReservationBeforeCallingProviderAndCompletesLocallyWithoutATransaction() {
        execution.requestAndExecute(request());

        CacheInvalidationBatch batch = onlyBatch();
        assertThat(provider.calls).isEqualTo(1);
        assertThat(provider.transactionActive).isFalse();
        assertThat(provider.durableStatusAtInvocation).isEqualTo(CacheInvalidationStatus.RUNNING);
        assertThat(provider.durableAttemptCountAtInvocation).isZero();
        assertThat(provider.durablePendingCountAtInvocation).isEqualTo(2);
        assertThat(batch.status()).isEqualTo(CacheInvalidationStatus.COMPLETED);
        assertThat(batch.completedAt()).isPresent();
        assertThat(batch.attempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.provider()).isEqualTo(CachePurgeProvider.LOCAL);
            assertThat(attempt.failureReason()).isNull();
        });
        assertThat(batch.targets()).allMatch(target -> target.status() == CacheTargetStatus.SKIPPED);
    }

    @Test
    void executesAnExistingRequestedBatch() {
        CacheInvalidationBatchId id = requests.request(request());

        execution.executeInitial(id);

        assertThat(repository.findById(id).orElseThrow().status()).isEqualTo(CacheInvalidationStatus.COMPLETED);
        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    void reservationFailureRollsBackCreationAndNeverCallsProvider() {
        repository.failAfterSaveVersion = 1L;

        assertThatCode(() -> execution.requestAndExecute(request())).doesNotThrowAnyException();

        assertThat(provider.calls).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operations.cache_invalidation_batches", Integer.class))
                .isZero();
    }

    @Test
    void resultPersistenceFailureRollsBackRootTargetsAndAttemptAndReturnsNormally() {
        repository.failAfterSaveVersion = 2L;

        assertThatCode(() -> execution.requestAndExecute(request())).doesNotThrowAnyException();

        CacheInvalidationBatch batch = onlyBatch();
        assertThat(provider.calls).isEqualTo(1);
        assertThat(batch.status()).isEqualTo(CacheInvalidationStatus.RUNNING);
        assertThat(batch.version()).isEqualTo(1);
        assertThat(batch.targets()).allMatch(target -> target.status() == CacheTargetStatus.PENDING);
        assertThat(batch.attempts()).isEmpty();
    }

    @Test
    void typedProviderFailureRecordsOneFailedAttemptWithoutAutomaticRetry() {
        providerBehavior = ProviderBehavior.RATE_LIMITED;

        execution.requestAndExecute(request());

        CacheInvalidationBatch batch = onlyBatch();
        assertThat(provider.calls).isEqualTo(1);
        assertThat(batch.status()).isEqualTo(CacheInvalidationStatus.FAILED);
        assertThat(batch.failureReason()).contains(CachePurgeFailureReason.RATE_LIMITED);
        assertThat(batch.attempts()).hasSize(1);
        assertThat(batch.targets()).allMatch(target -> target.status() == CacheTargetStatus.FAILED);
    }

    @Test
    void partialFailurePersistsSatisfiedAndFailedTargetsWithoutAutomaticRetry() {
        providerBehavior = ProviderBehavior.PARTIAL;

        execution.requestAndExecute(request());

        CacheInvalidationBatch batch = onlyBatch();
        assertThat(provider.calls).isEqualTo(1);
        assertThat(batch.status()).isEqualTo(CacheInvalidationStatus.PARTIAL);
        assertThat(batch.targets()).extracting(target -> target.status())
                .containsExactlyInAnyOrder(CacheTargetStatus.PURGED, CacheTargetStatus.FAILED);
    }

    @Test
    void manualRetrySendsOnlyTheTargetThatFailedDuringAPartialAttempt() {
        providerBehavior = ProviderBehavior.PARTIAL;
        execution.requestAndExecute(request());
        CacheInvalidationBatchId id = onlyBatch().id();
        CacheInvalidationBatch firstAttempt = repository.findById(id).orElseThrow();

        providerBehavior = ProviderBehavior.SKIP_SUCCESS;
        execution.executeManualRetry(id);

        CacheInvalidationBatch completed = repository.findById(id).orElseThrow();
        assertThat(provider.lastTargetCount).isEqualTo(1);
        assertThat(completed.status()).isEqualTo(CacheInvalidationStatus.COMPLETED);
        assertThat(completed.attempts()).hasSize(2);
        assertThat(completed.attempts().getFirst()).isEqualTo(firstAttempt.attempts().getFirst());
        assertThat(completed.targets()).extracting(target -> target.status())
                .containsExactlyInAnyOrder(CacheTargetStatus.PURGED, CacheTargetStatus.SKIPPED);
    }

    @Test
    void committedReservationWithoutProviderCallRemainsTruthfullyRunning() {
        CachePurgeTransactionService transactionService = context.getBean(CachePurgeTransactionService.class);

        var workItem = transactionService.createAndReserve(request());

        CacheInvalidationBatch batch = repository.findById(workItem.batchId()).orElseThrow();
        assertThat(batch.status()).isEqualTo(CacheInvalidationStatus.RUNNING);
        assertThat(batch.attempts()).isEmpty();
        assertThat(batch.targets()).allMatch(target -> target.status() == CacheTargetStatus.PENDING);
        assertThat(provider.calls).isZero();
    }

    @Test
    void manualRetriesUseOnlyFailedTargetsAndFourthAttemptIsImpossible() {
        providerBehavior = ProviderBehavior.RATE_LIMITED;
        execution.requestAndExecute(request());
        CacheInvalidationBatchId id = onlyBatch().id();

        execution.executeManualRetry(id);
        execution.executeManualRetry(id);
        CacheInvalidationBatch exhausted = repository.findById(id).orElseThrow();

        assertThat(provider.calls).isEqualTo(3);
        assertThat(exhausted.attempts()).extracting(attempt -> attempt.attemptNumber()).containsExactly(1, 2, 3);
        assertThat(exhausted.completedAt()).isPresent();
        assertThatCode(() -> execution.executeManualRetry(id)).doesNotThrowAnyException();
        assertThat(provider.calls).isEqualTo(3);
        assertThat(repository.findById(id).orElseThrow().attempts()).hasSize(3);
    }

    @Test
    void providerMayChangeToLocalForAManualRetry() {
        providerBehavior = ProviderBehavior.RATE_LIMITED;
        providerIdentity = CachePurgeProvider.CLOUDFLARE;
        restartContext();
        execution.requestAndExecute(request());
        CacheInvalidationBatchId id = onlyBatch().id();

        providerBehavior = ProviderBehavior.SKIP_SUCCESS;
        providerIdentity = CachePurgeProvider.LOCAL;
        restartContext();
        execution.executeManualRetry(id);

        CacheInvalidationBatch batch = repository.findById(id).orElseThrow();
        assertThat(batch.status()).isEqualTo(CacheInvalidationStatus.COMPLETED);
        assertThat(batch.attempts()).extracting(attempt -> attempt.provider())
                .containsExactly(CachePurgeProvider.CLOUDFLARE, CachePurgeProvider.LOCAL);
        assertThat(batch.targets()).allMatch(target -> target.status() == CacheTargetStatus.SKIPPED);
    }

    @Test
    void unexpectedProviderExceptionBecomesSafeFailedAttempt() {
        providerBehavior = ProviderBehavior.THROW;

        assertThatCode(() -> execution.requestAndExecute(request())).doesNotThrowAnyException();

        CacheInvalidationBatch batch = onlyBatch();
        assertThat(batch.failureReason()).contains(CachePurgeFailureReason.UNKNOWN_PROVIDER_FAILURE);
        assertThat(batch.attempts()).hasSize(1);
    }

    private void restartContext() {
        context.close();
        openContext();
    }

    private void openContext() {
        context = new AnnotationConfigApplicationContext(ExecutionTestConfiguration.class);
        repository = context.getBean(FailingRepository.class);
        provider = context.getBean(FakeProvider.class);
        execution = context.getBean(CacheInvalidationExecutionPort.class);
        requests = context.getBean(CacheInvalidationRequestPort.class);
    }

    private CacheInvalidationBatch onlyBatch() {
        List<CacheInvalidationBatch> batches = repository.findPendingBatches(100);
        if (!batches.isEmpty()) return batches.getFirst();
        List<CacheInvalidationBatch> failed = repository.findRecentFailures(100);
        if (!failed.isEmpty()) return failed.getFirst();
        CacheInvalidationBatchId id = CacheInvalidationBatchId.from(jdbc.queryForObject(
                "SELECT id FROM operations.cache_invalidation_batches LIMIT 1", java.util.UUID.class));
        return repository.findById(id).orElseThrow();
    }

    private static CacheInvalidationRequest request() {
        return new CacheInvalidationRequest(InvalidationReason.PUBLIC_RESOURCE_CHANGED, InvalidationRequester.SYSTEM,
                List.of(new CacheInvalidationTargetRequest(CacheTargetType.URL, "/articles/example"),
                        new CacheInvalidationTargetRequest(CacheTargetType.CACHE_TAG, "site:public-documents")));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class ExecutionTestConfiguration {
        @Bean DataSource dataSource() { return dataSource; }
        @Bean NamedParameterJdbcTemplate namedJdbc(DataSource source) { return new NamedParameterJdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
        @Bean FailingRepository batchRepository(
                ObjectProvider<NamedParameterJdbcTemplate> namedJdbc,
                ObjectProvider<TransactionTemplate> transactionTemplate) {
            return new FailingRepository(new JdbcCacheInvalidationBatchRepositoryAdapter(namedJdbc, transactionTemplate));
        }
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean CacheInvalidationRequestPort requestPort(FailingRepository batches, Clock clock) {
            return new CacheInvalidationRequestService(batches, clock);
        }
        @Bean CachePurgeTransactionService purgeTransactions(
                CacheInvalidationRequestPort requests, FailingRepository batches) {
            return new CachePurgeTransactionService(requests, batches);
        }
        @Bean FakeProvider provider() { return new FakeProvider(providerIdentity, jdbc); }
        @Bean NonTransactionalCachePurgeInvoker invoker(FakeProvider provider) {
            return new NonTransactionalCachePurgeInvoker(provider);
        }
        @Bean CachePurgeExecutionCoordinator coordinator(
                CachePurgeTransactionService transactions,
                NonTransactionalCachePurgeInvoker invoker,
                Clock clock) {
            return new CachePurgeExecutionCoordinator(transactions, invoker, clock);
        }
    }

    static final class FailingRepository implements CacheInvalidationBatchRepository {
        private final CacheInvalidationBatchRepository delegate;
        private Long failAfterSaveVersion;

        FailingRepository(CacheInvalidationBatchRepository delegate) { this.delegate = delegate; }

        @Override
        public void save(CacheInvalidationBatch batch) {
            delegate.save(batch);
            if (Long.valueOf(batch.version()).equals(failAfterSaveVersion)) {
                throw new IllegalStateException("forced persistence failure");
            }
        }
        @Override public Optional<CacheInvalidationBatch> findById(CacheInvalidationBatchId id) {
            return delegate.findById(id);
        }
        @Override public List<CacheInvalidationBatch> findPendingBatches(int limit) {
            return delegate.findPendingBatches(limit);
        }
        @Override public List<CacheInvalidationBatch> findRecentFailures(int limit) {
            return delegate.findRecentFailures(limit);
        }
    }

    static final class FakeProvider implements CachePurgePort {
        private final CachePurgeProvider identity;
        private final JdbcTemplate independentJdbc;
        private int calls;
        private boolean transactionActive;
        private CacheInvalidationStatus durableStatusAtInvocation;
        private int durableAttemptCountAtInvocation;
        private int durablePendingCountAtInvocation;
        private int lastTargetCount;

        FakeProvider(CachePurgeProvider identity, JdbcTemplate independentJdbc) {
            this.identity = identity;
            this.independentJdbc = independentJdbc;
        }

        @Override public CachePurgeProvider provider() { return identity; }

        @Override
        public CachePurgeProviderResult purge(CachePurgeProviderRequest request) {
            calls++;
            lastTargetCount = request.targets().size();
            transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            durableStatusAtInvocation = CacheInvalidationStatus.valueOf(independentJdbc.queryForObject(
                    "SELECT status FROM operations.cache_invalidation_batches WHERE id = ?",
                    String.class, request.batchId().value()));
            durableAttemptCountAtInvocation = independentJdbc.queryForObject(
                    "SELECT count(*) FROM operations.cache_purge_attempts WHERE batch_id = ?",
                    Integer.class, request.batchId().value());
            durablePendingCountAtInvocation = independentJdbc.queryForObject(
                    "SELECT count(*) FROM operations.cache_invalidation_targets WHERE batch_id = ? AND status = 'PENDING'",
                    Integer.class, request.batchId().value());
            if (providerBehavior == ProviderBehavior.THROW) throw new IllegalStateException("unsafe detail");
            if (providerBehavior == ProviderBehavior.SKIP_SUCCESS) {
                return CachePurgeProviderResult.success(request, request.targets().stream()
                        .map(target -> CacheTargetOutcome.of(target.targetId(), CacheTargetStatus.SKIPPED)).toList());
            }
            if (providerBehavior == ProviderBehavior.PARTIAL) {
                return CachePurgeProviderResult.failed(request, CachePurgeFailureReason.NETWORK_ERROR,
                        List.of(CacheTargetOutcome.of(request.targets().get(0).targetId(), CacheTargetStatus.PURGED),
                                CacheTargetOutcome.of(request.targets().get(1).targetId(), CacheTargetStatus.FAILED)));
            }
            return CachePurgeProviderResult.failed(request, CachePurgeFailureReason.RATE_LIMITED,
                    request.targets().stream()
                            .map(target -> CacheTargetOutcome.of(target.targetId(), CacheTargetStatus.FAILED)).toList());
        }
    }

    enum ProviderBehavior { SKIP_SUCCESS, RATE_LIMITED, PARTIAL, THROW }
}
