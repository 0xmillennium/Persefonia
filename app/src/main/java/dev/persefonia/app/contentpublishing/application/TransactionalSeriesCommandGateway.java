package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.app.audit.integration.ContentPublishingAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.contentpublishing.application.command.AddSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.ArchiveSeriesCommand;
import dev.persefonia.contentpublishing.application.command.CreateSeriesCommand;
import dev.persefonia.contentpublishing.application.command.RemoveSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.ReorderSeriesEntriesCommand;
import dev.persefonia.contentpublishing.application.command.SeriesResult;
import dev.persefonia.contentpublishing.application.command.UpdateSeriesCommand;
import dev.persefonia.contentpublishing.application.service.SeriesCommandGateway;
import dev.persefonia.contentpublishing.application.service.SeriesCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalSeriesCommandGateway implements SeriesCommandGateway {
    private final SeriesCommandService service;
    private final AppendAuditRecordPort audit;
    private final ContentPublishingAuditMapper auditMapper;

    public TransactionalSeriesCommandGateway(
            SeriesCommandService service,
            AppendAuditRecordPort audit,
            ContentPublishingAuditMapper auditMapper) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
    }

    @Override
    @Transactional
    public SeriesResult create(CreateSeriesCommand command) {
        SeriesResult result = service.create(command);
        audit.append(auditMapper.seriesCreated(command, result));
        return result;
    }

    @Override
    @Transactional
    public SeriesResult update(UpdateSeriesCommand command) {
        SeriesResult result = service.update(command);
        audit.append(auditMapper.seriesUpdated(command, result));
        return result;
    }

    @Override
    @Transactional
    public SeriesResult archive(ArchiveSeriesCommand command) {
        SeriesResult result = service.archive(command);
        if (result.mutated()) {
            audit.append(auditMapper.seriesArchived(command, result));
        }
        return result;
    }

    @Override
    @Transactional
    public SeriesResult addEntry(AddSeriesEntryCommand command) {
        SeriesResult result = service.addEntry(command);
        audit.append(auditMapper.seriesEntryAdded(command, result));
        return result;
    }

    @Override
    @Transactional
    public SeriesResult removeEntry(RemoveSeriesEntryCommand command) {
        SeriesResult result = service.removeEntry(command);
        audit.append(auditMapper.seriesEntryRemoved(command, result));
        return result;
    }

    @Override
    @Transactional
    public SeriesResult reorderEntries(ReorderSeriesEntriesCommand command) {
        SeriesResult result = service.reorderEntries(command);
        audit.append(auditMapper.seriesEntriesReordered(command, result));
        return result;
    }
}
