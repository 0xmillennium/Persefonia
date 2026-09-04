package dev.persefonia.app.webadmin.operations;

import dev.persefonia.platformoperations.application.operations.*;
import dev.persefonia.platformoperations.domain.cache.*;
import dev.persefonia.platformoperations.application.recovery.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

@TestConfiguration(proxyBeanMethods = false)
@Profile("admin-operations-mvc-test")
class AdminOperationsTestConfiguration {
    static final UUID BATCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    @Bean @Primary TestQueries operationsQueries() { return new TestQueries(); }
    @Bean @Primary TestRecovery operationsRecovery() { return new TestRecovery(); }
    @Bean @Primary OperationsHealthQueryPort operationsHealth() {
        return () -> new OperationsHealthSnapshot(
                OperationsComponentStatus.UP, OperationsComponentStatus.UP, OperationsComponentStatus.DOWN,
                OperationsComponentStatus.UP,
                CachePurgeProvider.LOCAL, OperationsComponentStatus.UP,
                new MigrationStatusSummary("21", "21", 0, MigrationStatus.UP_TO_DATE));
    }
    @Bean @Primary TestRecoveryVerification recoveryVerification() { return new TestRecoveryVerification(); }

    static final class TestRecoveryVerification implements RecoveryVerificationQueryPort {
        int contextCalls;
        int verifyCalls;
        private final RecoveryVerificationContext context = new RecoveryVerificationContext(
                new ApplicationReleaseInfo("persefonia", "0.1.0-SNAPSHOT"),
                new MigrationStatusSummary("21", "21", 0, MigrationStatus.UP_TO_DATE),
                OperationsComponentStatus.UP);
        @Override public RecoveryVerificationContext context() { contextCalls++; return context; }
        @Override public RecoveryVerificationReport verify() {
            verifyCalls++;
            return new RecoveryVerificationReport(context, RecoveryVerificationStatus.INCONSISTENT, NOW,
                    new RecoveryMediaIntegritySummary(2, 1, 0, 0, 1, 1,
                            List.of(new RecoveryMediaIssue(
                                    RecoveryMediaIssueCategory.CHECKSUM_MISMATCH,
                                    RecoveryMediaObjectKind.VARIANT, BATCH_ID, "<script>")), false),
                    new DurableAssetReferenceIntegritySummary(1, 1,
                            List.of(new DurableAssetReferenceIssue(
                                    DurableAssetReferenceKind.DISCOVERY_OG_IMAGE, BATCH_ID,
                                    UUID.fromString("22222222-2222-2222-2222-222222222222"))), false));
        }
    }

    static final class TestQueries implements CacheInvalidationOperationsQueryPort {
        CacheInvalidationOperationsSearchRequest request;
        CacheRecoveryAction detailAction = CacheRecoveryAction.RESUME_STRANDED;
        @Override public CacheInvalidationOperationsListPage search(CacheInvalidationOperationsSearchRequest request) {
            this.request = request;
            return new CacheInvalidationOperationsListPage(List.of(new CacheInvalidationOperationsListItem(
                    CacheInvalidationBatchId.from(BATCH_ID), NOW.minusSeconds(3600), CacheInvalidationStatus.RUNNING,
                    NOW.minusSeconds(1800), null, 1, 0, null, null, null,
                    CacheInvalidationAttentionState.STRANDED)), 26, request.page(), request.pageSize());
        }
        @Override public Optional<CacheInvalidationOperationsDetail> findById(CacheInvalidationBatchId id) {
            if (!id.value().equals(BATCH_ID)) return Optional.empty();
            return Optional.of(new CacheInvalidationOperationsDetail(
                    id, InvalidationReason.PUBLIC_RESOURCE_CHANGED, InvalidationRequester.SYSTEM,
                    NOW.minusSeconds(3600), CacheInvalidationStatus.RUNNING, NOW.minusSeconds(1800), null, null, 1,
                    List.of(new CacheInvalidationOperationsTarget(CacheTargetType.URL,
                            CacheTargetValue.url("/safe/<script>"), CacheTargetStatus.PENDING)),
                    List.of(), attention(detailAction), detailAction));
        }
        @Override public CacheInvalidationOperationsSummary summarize() {
            return new CacheInvalidationOperationsSummary(1, 2, 3, 4, 5, 6);
        }

        private static CacheInvalidationAttentionState attention(CacheRecoveryAction action) {
            return switch (action) {
                case EXECUTE_INITIAL -> CacheInvalidationAttentionState.PENDING_INITIAL;
                case RETRY_FAILED -> CacheInvalidationAttentionState.RETRY_AVAILABLE;
                case RESUME_STRANDED -> CacheInvalidationAttentionState.STRANDED;
                case NONE -> CacheInvalidationAttentionState.RUNNING;
            };
        }
    }

    static final class TestRecovery implements CacheInvalidationRecoveryGateway {
        CacheRecoveryCommandResult result = CacheRecoveryCommandResult.ACCEPTED;
        int initial; int retry; int resume;
        @Override public CacheRecoveryCommandResult requestInitialExecution(CacheInvalidationRecoveryCommand command) {
            initial++; return result;
        }
        @Override public CacheRecoveryCommandResult requestRetry(CacheInvalidationRecoveryCommand command) {
            retry++; return result;
        }
        @Override public CacheRecoveryCommandResult requestStrandedResume(CacheInvalidationRecoveryCommand command) {
            resume++; return result;
        }
    }
}
