package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.exception.SeriesNotFoundException;
import dev.persefonia.contentpublishing.application.port.SeriesCandidateContentReadModel;
import dev.persefonia.contentpublishing.application.query.SeriesCandidateContentItem;
import dev.persefonia.contentpublishing.application.query.SeriesEditView;
import dev.persefonia.contentpublishing.application.query.SeriesEntryView;
import dev.persefonia.contentpublishing.application.query.SeriesListItem;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntry;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSummary;
import dev.persefonia.contentpublishing.domain.model.series.port.SeriesRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SeriesAdminQueryService {
    private final ContentItemRepository contentItems;
    private final SeriesRepository seriesRepository;
    private final SeriesCandidateContentReadModel candidateReadModel;
    private final ContentCommandAuthorizationPolicy authorization;

    public SeriesAdminQueryService(
            ContentItemRepository contentItems,
            SeriesRepository seriesRepository,
            SeriesCandidateContentReadModel candidateReadModel,
            ContentCommandAuthorizationPolicy authorization) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
        this.seriesRepository = Objects.requireNonNull(seriesRepository, "seriesRepository");
        this.candidateReadModel = Objects.requireNonNull(candidateReadModel, "candidateReadModel");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public List<SeriesListItem> list(ContentCommandActor actor) {
        authorization.requireOwner(actor, "series.admin-list");
        return seriesRepository.findAllForAdmin().stream()
                .map(this::listItem)
                .toList();
    }

    public SeriesEditView edit(ContentCommandActor actor, SeriesId id) {
        authorization.requireOwner(actor, "series.admin-edit");
        Series series = seriesRepository.findById(id).orElseThrow(() -> new SeriesNotFoundException(id));
        return new SeriesEditView(
                series.id(),
                series.language(),
                series.slug().value(),
                series.title().value(),
                series.description().map(description -> description.value()),
                series.status(),
                entries(series),
                candidates(series));
    }

    private SeriesListItem listItem(SeriesSummary summary) {
        return new SeriesListItem(
                summary.id(),
                summary.language(),
                summary.slug().value(),
                summary.title().value(),
                summary.status(),
                summary.entryCount(),
                summary.updatedAt());
    }

    private List<SeriesEntryView> entries(Series series) {
        return series.entries().stream()
                .map(this::entryView)
                .toList();
    }

    private SeriesEntryView entryView(SeriesEntry entry) {
        ContentItem item = contentItems.findById(entry.contentItemId()).orElse(null);
        if (item == null) {
            return new SeriesEntryView(
                    entry.id(),
                    entry.contentItemId(),
                    entry.position().value(),
                    dev.persefonia.contentpublishing.domain.content.ContentType.ARTICLE,
                    ContentStatus.ARCHIVED,
                    Optional.of("Missing content item"));
        }
        return new SeriesEntryView(
                entry.id(),
                entry.contentItemId(),
                entry.position().value(),
                item.type(),
                item.status(),
                item.title().map(Title::value));
    }

    private List<SeriesCandidateContentItem> candidates(Series series) {
        return candidateReadModel.candidatesFor(series.id(), series.language());
    }
}
