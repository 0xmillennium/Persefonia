package dev.persefonia.app.platformoperations.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.app.audit.integration.AdminAuditCommandFactory;
import dev.persefonia.app.observability.CurrentRequestIdProvider;
import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.platformoperations.application.cache.*;
import dev.persefonia.platformoperations.application.operations.*;
import dev.persefonia.platformoperations.domain.cache.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class CacheInvalidationRecoveryCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final CacheOperationsCommandActor OWNER =
            new CacheOperationsCommandActor(UUID.randomUUID(), true, true);

    @Test
    void requestedBatchIsAuditedBeforeInitialExecution() {
        TestHarness harness = new TestHarness(requested(), false);

        CacheRecoveryCommandResult result = harness.gateway.requestInitialExecution(harness.command(OWNER));

        assertThat(result).isEqualTo(CacheRecoveryCommandResult.ACCEPTED);
        assertThat(harness.audit.records).singleElement().satisfies(record -> {
            assertThat(record.action()).isEqualTo("cache_invalidation.initial_execution.requested");
            assertThat(record.entityContext()).isEqualTo("platform_operations");
            assertThat(record.entityType()).isEqualTo("cache_invalidation_batch");
            assertThat(record.entityId()).isEqualTo(harness.batch.id().value());
            assertThat(record.metadata()).singleElement().satisfies(metadata -> {
                assertThat(metadata.key()).isEqualTo("attempt_number");
                assertThat(metadata.value()).isEqualTo("1");
            });
        });
        assertThat(harness.execution.initial).isEqualTo(1);
    }

    @Test
    void retryAndStrandedResumeUseServerDerivedAttemptNumbersAndExactActions() {
        CacheInvalidationBatch failed = failedOnce();
        TestHarness retry = new TestHarness(failed, false);
        assertThat(retry.gateway.requestRetry(retry.command(OWNER))).isEqualTo(CacheRecoveryCommandResult.ACCEPTED);
        assertThat(retry.audit.records.getFirst().action()).isEqualTo("cache_invalidation.retry.requested");
        assertThat(retry.audit.records.getFirst().metadata().getFirst().value()).isEqualTo("2");
        assertThat(retry.execution.retries).isEqualTo(1);

        CacheInvalidationBatch stranded = requested();
        stranded.beginInitialAttempt(NOW.minusSeconds(3600));
        TestHarness resume = new TestHarness(stranded, false);
        assertThat(resume.gateway.requestStrandedResume(resume.command(OWNER)))
                .isEqualTo(CacheRecoveryCommandResult.ACCEPTED);
        assertThat(resume.audit.records.getFirst().action())
                .isEqualTo("cache_invalidation.stranded_resume.requested");
        assertThat(resume.audit.records.getFirst().metadata().getFirst().value()).isEqualTo("1");
        assertThat(resume.execution.resumes).isEqualTo(1);
    }

    @Test
    void nonOwnerIsRejectedBeforeReadAuditOrExecution() {
        TestHarness harness = new TestHarness(requested(), false);
        CacheOperationsCommandActor editor = new CacheOperationsCommandActor(UUID.randomUUID(), true, false);

        assertThatThrownBy(() -> harness.gateway.requestInitialExecution(harness.command(editor)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(harness.repository.reads).isZero();
        assertThat(harness.audit.records).isEmpty();
        assertThat(harness.execution.total()).isZero();
    }

    @Test
    void mandatoryAuditFailurePreventsExecutionAndIsNotAccepted() {
        TestHarness harness = new TestHarness(requested(), true);

        assertThatThrownBy(() -> harness.gateway.requestInitialExecution(harness.command(OWNER)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(harness.execution.total()).isZero();
        assertThat(harness.batch.status()).isEqualTo(CacheInvalidationStatus.REQUESTED);
    }

    @Test
    void invalidAndExhaustedStatesHaveNoSideEffectsOrAttemptFour() {
        CacheInvalidationBatch completed = requested();
        completed.beginInitialAttempt(NOW.minusSeconds(30));
        completed.recordAttemptResult(1, CachePurgeProvider.LOCAL, NOW.minusSeconds(20),
                CachePurgeResult.SUCCESS, null, outcomes(completed, CacheTargetStatus.SKIPPED), NOW.minusSeconds(10));
        TestHarness wrong = new TestHarness(completed, false);
        assertThat(wrong.gateway.requestRetry(wrong.command(OWNER))).isEqualTo(CacheRecoveryCommandResult.NOT_ELIGIBLE);
        assertThat(wrong.audit.records).isEmpty();

        CacheInvalidationBatch exhausted = failedThreeTimes();
        TestHarness budget = new TestHarness(exhausted, false);
        assertThat(budget.gateway.requestRetry(budget.command(OWNER)))
                .isEqualTo(CacheRecoveryCommandResult.NOT_ELIGIBLE);
        assertThat(budget.execution.total()).isZero();
        assertThat(budget.audit.records).isEmpty();
        assertThat(exhausted.attempts()).hasSize(3);
    }

    private static CacheInvalidationBatch requested() {
        return CacheInvalidationBatch.request(CacheInvalidationBatchId.newId(),
                InvalidationReason.PUBLIC_RESOURCE_CHANGED, InvalidationRequester.SYSTEM,
                NOW.minusSeconds(7200), List.of(CacheInvalidationTarget.pending(
                        CacheInvalidationTargetId.newId(), CacheTargetType.URL, CacheTargetValue.url("/safe"))));
    }

    private static CacheInvalidationBatch failedOnce() {
        CacheInvalidationBatch batch = requested();
        batch.beginInitialAttempt(NOW.minusSeconds(120));
        batch.recordAttemptResult(1, CachePurgeProvider.LOCAL, NOW.minusSeconds(110), CachePurgeResult.FAILED,
                CachePurgeFailureReason.TIMEOUT, outcomes(batch, CacheTargetStatus.FAILED), NOW.minusSeconds(100));
        return batch;
    }

    private static CacheInvalidationBatch failedThreeTimes() {
        CacheInvalidationBatch batch = requested();
        for (int number = 1; number <= 3; number++) {
            Instant reservation = NOW.minusSeconds(120 - number * 20L);
            if (number == 1) batch.beginInitialAttempt(reservation); else batch.beginManualRetry(reservation);
            batch.recordAttemptResult(number, CachePurgeProvider.LOCAL, reservation.plusSeconds(1),
                    CachePurgeResult.FAILED, CachePurgeFailureReason.TIMEOUT,
                    outcomes(batch, CacheTargetStatus.FAILED), reservation.plusSeconds(2));
        }
        return batch;
    }

    private static List<CacheTargetOutcome> outcomes(CacheInvalidationBatch batch, CacheTargetStatus status) {
        return batch.targets().stream().filter(target -> target.status() == CacheTargetStatus.PENDING)
                .map(target -> CacheTargetOutcome.of(target.id(), status)).toList();
    }

    private static final class TestHarness {
        final CacheInvalidationBatch batch;
        final FakeRepository repository;
        final CapturingAudit audit;
        final CapturingExecution execution = new CapturingExecution();
        final CacheInvalidationRecoveryGateway gateway;

        TestHarness(CacheInvalidationBatch batch, boolean failAudit) {
            this.batch = batch;
            this.repository = new FakeRepository(batch);
            this.audit = new CapturingAudit(failAudit);
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            var policy = new CacheInvalidationRecoveryPolicy(Duration.ofMinutes(15));
            var preflight = new RecoveryRequestTransactionService(
                    new IdentityAccessCacheOperationsCommandAuthorizationPolicy(), repository, policy, audit,
                    new AdminAuditCommandFactory(clock, new CurrentRequestIdProvider()), clock);
            gateway = new CacheInvalidationRecoveryCoordinator(preflight, execution);
        }
        CacheInvalidationRecoveryCommand command(CacheOperationsCommandActor actor) {
            return new CacheInvalidationRecoveryCommand(actor, batch.id(), NOW);
        }
    }

    private static final class FakeRepository implements CacheInvalidationBatchRepository {
        final CacheInvalidationBatch batch; int reads;
        FakeRepository(CacheInvalidationBatch batch) { this.batch = batch; }
        @Override public void save(CacheInvalidationBatch batch) { }
        @Override public Optional<CacheInvalidationBatch> findById(CacheInvalidationBatchId id) {
            reads++; return batch.id().equals(id) ? Optional.of(batch) : Optional.empty();
        }
        @Override public List<CacheInvalidationBatch> findPendingBatches(int limit) { return List.of(); }
        @Override public List<CacheInvalidationBatch> findRecentFailures(int limit) { return List.of(); }
    }

    private static final class CapturingAudit implements AppendAuditRecordPort {
        final List<AppendAuditRecordCommand> records = new ArrayList<>(); final boolean fail;
        CapturingAudit(boolean fail) { this.fail = fail; }
        @Override public void append(AppendAuditRecordCommand command) {
            if (fail) throw new IllegalStateException("forced audit failure"); records.add(command);
        }
    }

    private static final class CapturingExecution implements CacheInvalidationExecutionPort {
        int initial; int retries; int resumes;
        @Override public void requestAndExecute(CacheInvalidationRequest request) { }
        @Override public void executeInitial(CacheInvalidationBatchId batchId) { initial++; }
        @Override public void executeManualRetry(CacheInvalidationBatchId batchId) { retries++; }
        @Override public void resumeStranded(CacheInvalidationBatchId batchId) { resumes++; }
        int total() { return initial + retries + resumes; }
    }
}
