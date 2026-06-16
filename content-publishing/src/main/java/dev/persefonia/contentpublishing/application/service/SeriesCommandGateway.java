package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.command.AddSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.ArchiveSeriesCommand;
import dev.persefonia.contentpublishing.application.command.CreateSeriesCommand;
import dev.persefonia.contentpublishing.application.command.RemoveSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.ReorderSeriesEntriesCommand;
import dev.persefonia.contentpublishing.application.command.SeriesResult;
import dev.persefonia.contentpublishing.application.command.UpdateSeriesCommand;

public interface SeriesCommandGateway {
    SeriesResult create(CreateSeriesCommand command);

    SeriesResult update(UpdateSeriesCommand command);

    SeriesResult archive(ArchiveSeriesCommand command);

    SeriesResult addEntry(AddSeriesEntryCommand command);

    SeriesResult removeEntry(RemoveSeriesEntryCommand command);

    SeriesResult reorderEntries(ReorderSeriesEntriesCommand command);
}
