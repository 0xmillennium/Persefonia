package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.app.audit.integration.ContentPublishingAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.contentpublishing.application.command.AddTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.CreateTranslationGroupCommand;
import dev.persefonia.contentpublishing.application.command.RemoveTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.TranslationGroupResult;
import dev.persefonia.contentpublishing.application.service.TranslationGroupCommandGateway;
import dev.persefonia.contentpublishing.application.service.TranslationGroupCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalTranslationGroupCommandGateway implements TranslationGroupCommandGateway {
    private final TranslationGroupCommandService service;
    private final AppendAuditRecordPort audit;
    private final ContentPublishingAuditMapper auditMapper;

    public TransactionalTranslationGroupCommandGateway(
            TranslationGroupCommandService service,
            AppendAuditRecordPort audit,
            ContentPublishingAuditMapper auditMapper) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
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
        return result;
    }

    @Override
    @Transactional
    public TranslationGroupResult removeEntry(RemoveTranslationEntryCommand command) {
        TranslationGroupResult result = service.removeEntry(command);
        audit.append(auditMapper.translationGroupEntryRemoved(command, result));
        return result;
    }
}
