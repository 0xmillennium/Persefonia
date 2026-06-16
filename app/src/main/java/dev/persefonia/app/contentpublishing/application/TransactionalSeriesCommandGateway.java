package dev.persefonia.app.contentpublishing.application;

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

    public TransactionalSeriesCommandGateway(SeriesCommandService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    @Transactional
    public SeriesResult create(CreateSeriesCommand command) {
        return service.create(command);
    }

    @Override
    @Transactional
    public SeriesResult update(UpdateSeriesCommand command) {
        return service.update(command);
    }

    @Override
    @Transactional
    public SeriesResult archive(ArchiveSeriesCommand command) {
        return service.archive(command);
    }

    @Override
    @Transactional
    public SeriesResult addEntry(AddSeriesEntryCommand command) {
        return service.addEntry(command);
    }

    @Override
    @Transactional
    public SeriesResult removeEntry(RemoveSeriesEntryCommand command) {
        return service.removeEntry(command);
    }

    @Override
    @Transactional
    public SeriesResult reorderEntries(ReorderSeriesEntriesCommand command) {
        return service.reorderEntries(command);
    }
}
