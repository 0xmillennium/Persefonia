package dev.persefonia.contentpublishing.application.service;

import static dev.persefonia.contentpublishing.application.service.ContentApplicationSupport.requiredContent;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.command.AddSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.ArchiveSeriesCommand;
import dev.persefonia.contentpublishing.application.command.CreateSeriesCommand;
import dev.persefonia.contentpublishing.application.command.RemoveSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.ReorderSeriesEntriesCommand;
import dev.persefonia.contentpublishing.application.command.SeriesResult;
import dev.persefonia.contentpublishing.application.command.UpdateSeriesCommand;
import dev.persefonia.contentpublishing.application.discovery.SeriesDiscoverabilityCoordinator;
import dev.persefonia.contentpublishing.application.discovery.SeriesPublicRouteFactory;
import dev.persefonia.contentpublishing.application.exception.SeriesCommandRejectedException;
import dev.persefonia.contentpublishing.application.exception.SeriesNotFoundException;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesDescription;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntryId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSlug;
import dev.persefonia.contentpublishing.domain.model.series.SeriesTitle;
import dev.persefonia.contentpublishing.domain.model.series.SeriesValidationException;
import dev.persefonia.contentpublishing.domain.model.series.port.SeriesRepository;
import java.util.Objects;

public final class SeriesCommandService {
    private final ContentItemRepository contentItems;
    private final SeriesRepository seriesRepository;
    private final ContentCommandAuthorizationPolicy authorization;
    private final SeriesDiscoverabilityCoordinator discoverability;
    private final SeriesPublicRouteFactory publicRoutes;

    public SeriesCommandService(
            ContentItemRepository contentItems,
            SeriesRepository seriesRepository,
            ContentCommandAuthorizationPolicy authorization,
            SeriesDiscoverabilityCoordinator discoverability) {
        this(contentItems, seriesRepository, authorization, discoverability, new SeriesPublicRouteFactory());
    }

    public SeriesCommandService(
            ContentItemRepository contentItems,
            SeriesRepository seriesRepository,
            ContentCommandAuthorizationPolicy authorization,
            SeriesDiscoverabilityCoordinator discoverability,
            SeriesPublicRouteFactory publicRoutes) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
        this.seriesRepository = Objects.requireNonNull(seriesRepository, "seriesRepository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.discoverability = Objects.requireNonNull(discoverability, "discoverability");
        this.publicRoutes = Objects.requireNonNull(publicRoutes, "publicRoutes");
    }

    public SeriesResult create(CreateSeriesCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "series.create");
        SeriesSlug slug = SeriesSlug.of(command.slug());
        if (seriesRepository.existsByLanguageAndSlug(command.language(), slug)) {
            throw duplicateSlug();
        }
        Series series = Series.create(
                SeriesId.newId(),
                command.language(),
                slug,
                SeriesTitle.of(command.title()),
                SeriesDescription.optional(command.description()).orElse(null),
                command.requestedAt());
        Series saved = seriesRepository.save(series);
        discoverability.sync(saved);
        return result(saved, null, null, true);
    }

    public SeriesResult update(UpdateSeriesCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "series.update");
        Series series = requiredSeries(command.seriesId());
        var oldRoute = publicRoutes.publicUrl(series);
        rejectArchived(series);
        SeriesSlug slug = SeriesSlug.of(command.slug());
        seriesRepository.findByLanguageAndSlug(series.language(), slug)
                .filter(existing -> !existing.id().equals(series.id()))
                .ifPresent(existing -> {
                    throw duplicateSlug();
                });
        try {
            series.updateMetadata(
                    SeriesTitle.of(command.title()),
                    slug,
                    SeriesDescription.optional(command.description()).orElse(null),
                    command.requestedAt());
        } catch (SeriesValidationException exception) {
            throw translate(exception);
        }
        Series saved = seriesRepository.save(series);
        discoverability.sync(saved);
        return result(saved, null, oldRoute, true);
    }

    public SeriesResult archive(ArchiveSeriesCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "series.archive");
        Series series = requiredSeries(command.seriesId());
        if (series.isArchived()) {
            return result(series, null, publicRoutes.publicUrl(series), false);
        }
        series.archive(command.requestedAt());
        Series saved = seriesRepository.save(series);
        discoverability.sync(saved);
        return result(saved, null, publicRoutes.publicUrl(saved), true);
    }

    public SeriesResult addEntry(AddSeriesEntryCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "series.add-entry");
        Series series = requiredSeries(command.seriesId());
        rejectArchived(series);
        ContentItem item = requiredContent(contentItems, command.contentItemId());
        if (item.language() != series.language()) {
            throw new SeriesCommandRejectedException(
                    SeriesCommandRejectedException.Reason.LANGUAGE_MISMATCH,
                    "Series entries must use content in the same language as the series.");
        }
        if (item.status() == ContentStatus.ARCHIVED) {
            throw new SeriesCommandRejectedException(
                    SeriesCommandRejectedException.Reason.ARCHIVED_CONTENT,
                    "Archived content cannot be newly added to a series.");
        }
        if (series.containsContentItem(item.id())) {
            throw duplicateEntry();
        }
        try {
            series.addEntry(SeriesEntryId.newId(), item.id(), command.requestedAt());
        } catch (SeriesValidationException exception) {
            throw translate(exception);
        }
        Series saved = seriesRepository.save(series);
        return result(saved, null, publicRoutes.publicUrl(saved), true);
    }

    public SeriesResult removeEntry(RemoveSeriesEntryCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "series.remove-entry");
        Series series = requiredSeries(command.seriesId());
        rejectArchived(series);
        var removedEntry = series.entries().stream()
                .filter(entry -> entry.id().equals(command.entryId()))
                .findFirst();
        if (removedEntry.isEmpty()) {
            throw new SeriesCommandRejectedException(
                    SeriesCommandRejectedException.Reason.ENTRY_NOT_FOUND,
                    "The series entry does not exist.");
        }
        try {
            series.removeEntry(command.entryId(), command.requestedAt());
        } catch (SeriesValidationException exception) {
            throw translate(exception);
        }
        Series saved = seriesRepository.save(series);
        return result(saved, removedEntry.orElseThrow().contentItemId(), publicRoutes.publicUrl(saved), true);
    }

    public SeriesResult reorderEntries(ReorderSeriesEntriesCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "series.reorder-entries");
        Series series = requiredSeries(command.seriesId());
        rejectArchived(series);
        try {
            series.reorderEntries(command.orderedEntryIds(), command.requestedAt());
        } catch (SeriesValidationException exception) {
            throw new SeriesCommandRejectedException(
                    SeriesCommandRejectedException.Reason.INVALID_REORDER,
                    "The entry order must include each current entry exactly once.");
        }
        Series saved = seriesRepository.save(series);
        return result(saved, null, publicRoutes.publicUrl(saved), true);
    }

    private SeriesResult result(Series series, ContentId contentItemId,
            dev.persefonia.discovery.application.contract.PublicUrl oldRoute, boolean mutated) {
        return new SeriesResult(series.id(), contentItemId, mutated,
                java.util.Optional.ofNullable(oldRoute),
                java.util.Optional.of(publicRoutes.publicUrl(series)));
    }

    private Series requiredSeries(SeriesId id) {
        return seriesRepository.findById(id).orElseThrow(() -> new SeriesNotFoundException(id));
    }

    private static void rejectArchived(Series series) {
        if (series.isArchived()) {
            throw new SeriesCommandRejectedException(
                    SeriesCommandRejectedException.Reason.ARCHIVED_SERIES,
                    "Archived series cannot be changed.");
        }
    }

    private static SeriesCommandRejectedException duplicateSlug() {
        return new SeriesCommandRejectedException(
                SeriesCommandRejectedException.Reason.DUPLICATE_SLUG,
                "A series with this slug already exists for the language.");
    }

    private static SeriesCommandRejectedException duplicateEntry() {
        return new SeriesCommandRejectedException(
                SeriesCommandRejectedException.Reason.DUPLICATE_ENTRY,
                "This content item is already in the series.");
    }

    private static SeriesCommandRejectedException translate(SeriesValidationException exception) {
        String message = exception.getMessage();
        if (message != null && message.contains("already part")) {
            return duplicateEntry();
        }
        if (message != null && message.contains("archived series")) {
            return new SeriesCommandRejectedException(
                    SeriesCommandRejectedException.Reason.ARCHIVED_SERIES,
                    "Archived series cannot be changed.");
        }
        if (message != null && message.contains("does not exist")) {
            return new SeriesCommandRejectedException(
                    SeriesCommandRejectedException.Reason.ENTRY_NOT_FOUND,
                    "The series entry does not exist.");
        }
        return new SeriesCommandRejectedException(
                SeriesCommandRejectedException.Reason.INVALID_REORDER,
                "The series command was rejected.");
    }
}
