package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.app.audit.integration.ContentPublishingAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationRegistrar;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationSignal;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationSignal.TranslationChange;
import dev.persefonia.contentpublishing.application.command.AddTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.CreateTranslationGroupCommand;
import dev.persefonia.contentpublishing.application.command.RemoveTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.TranslationGroupResult;
import dev.persefonia.contentpublishing.application.service.TranslationGroupCommandGateway;
import dev.persefonia.contentpublishing.application.service.TranslationGroupCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class TransactionalTranslationGroupCommandGateway implements TranslationGroupCommandGateway {
    private final TranslationGroupCommandService service;
    private final AppendAuditRecordPort audit;
    private final ContentPublishingAuditMapper auditMapper;
    private final PublicCacheInvalidationRegistrar cacheInvalidation;

    public TransactionalTranslationGroupCommandGateway(
            TranslationGroupCommandService service,
            AppendAuditRecordPort audit,
            ContentPublishingAuditMapper auditMapper) {
        this(service, audit, auditMapper, PublicCacheInvalidationRegistrar.noOp());
    }

    @Autowired
    public TransactionalTranslationGroupCommandGateway(
            TranslationGroupCommandService service,
            AppendAuditRecordPort audit,
            ContentPublishingAuditMapper auditMapper,
            PublicCacheInvalidationRegistrar cacheInvalidation) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
        this.cacheInvalidation = Objects.requireNonNull(cacheInvalidation, "cacheInvalidation");
    }

    @Override
    @Transactional
    public TranslationGroupResult create(CreateTranslationGroupCommand command) {
        TranslationGroupResult result = service.create(command);
        audit.append(auditMapper.translationGroupCreated(command, result));
        return result;
    }

    @Override
    @Transactional
    public TranslationGroupResult addEntry(AddTranslationEntryCommand command) {
        TranslationGroupResult result = service.addEntry(command);
        audit.append(auditMapper.translationGroupEntryAdded(command, result));
        cacheInvalidation.register(new PublicCacheInvalidationSignal.TranslationGroupChanged(TranslationChange.ADD, result));
        return result;
    }

    @Override
    @Transactional
    public TranslationGroupResult removeEntry(RemoveTranslationEntryCommand command) {
        TranslationGroupResult result = service.removeEntry(command);
        audit.append(auditMapper.translationGroupEntryRemoved(command, result));
        cacheInvalidation.register(new PublicCacheInvalidationSignal.TranslationGroupChanged(TranslationChange.REMOVE, result));
        return result;
    }
}
