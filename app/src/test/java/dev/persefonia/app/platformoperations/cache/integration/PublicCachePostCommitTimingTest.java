package dev.persefonia.app.platformoperations.cache.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.persefonia.app.audit.integration.ContentPublishingAuditMapper;
import dev.persefonia.app.contentpublishing.application.TransactionalContentApplicationGateway;
import dev.persefonia.app.transaction.SpringTransactionSynchronizationPostCommitTaskExecutor;
import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.contentpublishing.application.command.ContentPublishResult;
import dev.persefonia.contentpublishing.application.command.PublishContentCommand;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicExposureSnapshot;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicMutationFacts;
import dev.persefonia.contentpublishing.application.service.ContentCommandService;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationExecutionPort;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequest;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.taxonomy.application.discovery.TagPublicRouteFactory;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class PublicCachePostCommitTimingTest {
    @Test
    void contentPublishDoesNotExecuteBeforeSourceCommitAndExecutesOnceAfterCommit() {
        AtomicInteger executions = new AtomicInteger();
        CacheInvalidationExecutionPort execution = new CacheInvalidationExecutionPort() {
            @Override public void requestAndExecute(CacheInvalidationRequest request) { executions.incrementAndGet(); }
            @Override public void executeInitial(CacheInvalidationBatchId batchId) { }
            @Override public void executeManualRetry(CacheInvalidationBatchId batchId) { }
        };
        var coordinator = new PublicCacheInvalidationCoordinator(
                (id, limit) -> new dev.persefonia.contentpublishing.application.publicview.ContentPublicSurfaceDependencies(
                        List.of(), List.of(), List.of(), false),
                (id, limit) -> List.of(), (ids, language, limit) -> List.of(),
                (id, limit) -> List.of(), (id, limit) -> List.of(), execution,
                new PublicCacheTargetPlanner(), new TagPublicRouteFactory(), new ProjectPublicRouteFactory());
        var registrar = new PublicCacheInvalidationRegistrar(
                new SpringTransactionSynchronizationPostCommitTaskExecutor(), coordinator);

        ContentId contentId = ContentId.newId();
        var exposed = new ContentPublicExposureSnapshot(true, true, true, true);
        var facts = new ContentPublicMutationFacts(
                contentId, ContentPublicExposureSnapshot.none(), exposed,
                Optional.empty(), Optional.of(new PublicUrl("/en/articles/published")));
        ContentPublishResult result = mock(ContentPublishResult.class);
        when(result.publicMutationFacts()).thenReturn(facts);
        PublishContentCommand command = mock(PublishContentCommand.class);
        ContentCommandService service = mock(ContentCommandService.class);
        when(service.publishContent(command)).thenReturn(result);
        ContentPublishingAuditMapper auditMapper = mock(ContentPublishingAuditMapper.class);
        when(auditMapper.published(command, result)).thenReturn(mock(AppendAuditRecordCommand.class));
        AppendAuditRecordPort audit = ignored -> { };
        var gateway = new TransactionalContentApplicationGateway(service, audit, auditMapper, registrar);
        var transactions = new TransactionTemplate(new InMemoryTransactionManager());

        transactions.executeWithoutResult(status -> {
            gateway.publishContent(command);
            assertThat(executions).hasValue(0);
        });

        assertThat(executions).hasValue(1);
    }

    private static final class InMemoryTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
