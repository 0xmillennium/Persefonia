package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.contentpublishing.application.discovery.ConfiguredContentCanonicalUrlFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.port.PublicSeriesEntryReadModel;
import dev.persefonia.contentpublishing.application.port.PublicTaggedContentReadModel;
import dev.persefonia.contentpublishing.application.port.PublicTranslationReadModel;
import dev.persefonia.contentpublishing.application.port.SeriesCandidateContentReadModel;
import dev.persefonia.contentpublishing.application.port.TranslationCandidateContentReadModel;
import dev.persefonia.contentpublishing.application.query.PublicContentPageResult;
import dev.persefonia.contentpublishing.application.query.PublicHreflangLink;
import dev.persefonia.contentpublishing.application.query.PublicSeriesEntryItem;
import dev.persefonia.contentpublishing.application.query.PublicTaggedContentItem;
import dev.persefonia.contentpublishing.application.query.PublicTaggedContentQuery;
import dev.persefonia.contentpublishing.application.query.PublicTranslationLink;
import dev.persefonia.contentpublishing.application.query.PublicTranslationLinkSet;
import dev.persefonia.contentpublishing.application.query.SeriesCandidateContentItem;
import dev.persefonia.contentpublishing.application.query.TranslationCandidateItem;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSlug;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSummary;
import dev.persefonia.contentpublishing.domain.model.series.port.SeriesRepository;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroup;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntry;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import dev.persefonia.contentpublishing.domain.translation.port.TranslationGroupRepository;
import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class InMemoryContentReadModelAdapter implements
        PublicTaggedContentReadModel,
        PublicSeriesEntryReadModel,
        PublicTranslationReadModel,
        SeriesCandidateContentReadModel,
        TranslationCandidateContentReadModel {
    private final ContentItemRepository contentItems;
    private final SeriesRepository seriesRepository;
    private final TranslationGroupRepository translationGroups;
    private final ContentPublicRouteFactory routeFactory = new ContentPublicRouteFactory();
    private final ConfiguredContentCanonicalUrlFactory canonicalUrlFactory =
            new ConfiguredContentCanonicalUrlFactory("https://0xmillennium.dev");

    public InMemoryContentReadModelAdapter(
            ContentItemRepository contentItems,
            SeriesRepository seriesRepository,
            TranslationGroupRepository translationGroups) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
        this.seriesRepository = Objects.requireNonNull(seriesRepository, "seriesRepository");
        this.translationGroups = Objects.requireNonNull(translationGroups, "translationGroups");
    }

    public static SeriesRepository emptySeriesRepository() {
        return new SeriesRepository() {
            @Override public Series save(Series series) { return series; }
            @Override public Optional<Series> findById(SeriesId id) { return Optional.empty(); }
            @Override public Optional<Series> findByLanguageAndSlug(ContentLanguage language, SeriesSlug slug) { return Optional.empty(); }
            @Override public boolean existsByLanguageAndSlug(ContentLanguage language, SeriesSlug slug) { return false; }
            @Override public List<SeriesSummary> findAllForAdmin() { return List.of(); }
        };
    }

    public static TranslationGroupRepository emptyTranslationGroupRepository() {
        return new TranslationGroupRepository() {
            @Override public TranslationGroup save(TranslationGroup group) { return group; }
            @Override public Optional<TranslationGroup> findById(TranslationGroupId id) { return Optional.empty(); }
            @Override public Optional<TranslationGroup> findByContentItemId(ContentId contentItemId) { return Optional.empty(); }
            @Override public boolean contentItemBelongsToAnyGroup(ContentId contentItemId) { return false; }
        };
    }

    @Override
    public List<PublicTaggedContentItem> list(PublicTaggedContentQuery query) {
        return contentItems.findByAssignedTagId(query.tagId()).stream()
                .filter(ContentItem::isListedPublicly)
                .filter(item -> item.language() == query.language())
                .filter(item -> item.renderSnapshot().isPresent())
                .filter(this::currentPublicPathIsValid)
                .sorted(Comparator.comparing((ContentItem item) -> item.publishedAt().orElseThrow())
                        .reversed()
                        .thenComparing(item -> item.id().value()))
                .limit(query.limit())
                .map(item -> new PublicTaggedContentItem(
                        item.title().orElseThrow().value(),
                        item.summary().orElseThrow().value(),
                        publicUrl(item),
                        publicUrl(item),
                        item.type().name(),
                        item.publishedAt().orElseThrow(),
                        item.language().name()))
                .toList();
    }

    @Override
    public List<PublicSeriesEntryItem> listEntries(SeriesId seriesId, ContentLanguage language) {
        return seriesRepository.findById(seriesId).stream()
                .flatMap(series -> series.entries().stream()
                        .map(entry -> contentItems.findById(entry.contentItemId())
                                .filter(ContentItem::isListedPublicly)
                                .filter(item -> item.language() == language)
                                .filter(item -> item.renderSnapshot().isPresent())
                                .filter(this::currentPublicPathIsValid)
                                .map(item -> new PublicSeriesEntryItem(
                                        entry.position().value(),
                                        item.title().orElseThrow().value(),
                                        item.summary().orElseThrow().value(),
                                        publicUrl(item),
                                        publicUrl(item),
                                        item.type().name(),
                                        item.publishedAt().orElseThrow(),
                                        item.language().name())))
                        .flatMap(Optional::stream))
                .toList();
    }

    @Override
    public PublicTranslationLinkSet linksFor(PublicContentPageResult currentPage) {
        Optional<RenderableContent> current = contentItems.findById(currentPage.contentId())
                .flatMap(this::renderableContent)
                .filter(candidate -> candidate.publicUrl().equals(currentPage.canonicalPath().value()));
        if (current.isEmpty()) {
            return PublicTranslationLinkSet.empty();
        }
        return translationGroups.findByContentItemId(currentPage.contentId())
                .map(group -> linkSet(current.orElseThrow(), group.entries()))
                .orElseGet(PublicTranslationLinkSet::empty);
    }

    @Override
    public List<SeriesCandidateContentItem> candidatesFor(SeriesId seriesId, ContentLanguage language) {
        return manageableContent()
                .filter(item -> item.language() == language)
                .filter(item -> seriesRepository.findById(seriesId)
                        .map(series -> !series.containsContentItem(item.id()))
                        .orElse(true))
                .sorted(Comparator.comparing(ContentItem::updatedAt).reversed())
                .map(item -> new SeriesCandidateContentItem(
                        item.id(), item.type(), item.status(), item.title().map(Title::value)))
                .toList();
    }

    @Override
    public List<TranslationCandidateItem> candidatesFor(TranslationGroupId groupId, ContentType contentType) {
        TranslationGroup group = translationGroups.findById(groupId).orElseThrow();
        return manageableContent()
                .filter(item -> item.type() == contentType)
                .filter(item -> !group.containsLanguage(item.language()))
                .filter(item -> !translationGroups.contentItemBelongsToAnyGroup(item.id()))
                .sorted(Comparator.comparing(ContentItem::updatedAt).reversed())
                .map(item -> new TranslationCandidateItem(item.id(), item.language(), item.title().map(Title::value)))
                .toList();
    }

    private PublicTranslationLinkSet linkSet(RenderableContent current, List<TranslationGroupEntry> entries) {
        List<PublicTranslationLink> visibleLinks = entries.stream()
                .filter(entry -> !entry.contentItemId().equals(current.contentId()))
                .map(entry -> contentItems.findById(entry.contentItemId()).flatMap(this::renderableContent))
                .flatMap(Optional::stream)
                .map(content -> new PublicTranslationLink(
                        languageCode(content.language()),
                        languageLabel(content.language()),
                        content.title(),
                        content.publicUrl(),
                        canonicalUrl(content.publicUrl())))
                .toList();
        if (visibleLinks.isEmpty()) {
            return PublicTranslationLinkSet.empty();
        }
        List<PublicHreflangLink> hreflangLinks = new ArrayList<>();
        hreflangLinks.add(new PublicHreflangLink(languageCode(current.language()), canonicalUrl(current.publicUrl())));
        visibleLinks.stream()
                .map(link -> new PublicHreflangLink(link.language(), link.canonicalUrl()))
                .forEach(hreflangLinks::add);
        return PublicTranslationLinkSet.withAlternates(visibleLinks, hreflangLinks);
    }

    private Optional<RenderableContent> renderableContent(ContentItem item) {
        if (!item.isListedPublicly() || item.renderSnapshot().isEmpty() || !currentPublicPathIsValid(item)) {
            return Optional.empty();
        }
        return item.title().map(title -> new RenderableContent(
                item.id(), item.language(), title.value(), publicUrl(item)));
    }

    private boolean currentPublicPathIsValid(ContentItem item) {
        return item.metadata().canonicalPath()
                .map(canonicalPath -> canonicalPath.value().equals(publicUrl(item)))
                .orElse(false);
    }

    private String publicUrl(ContentItem item) {
        return routeFactory.publicUrl(item.type(), item.language(), item.slug().orElseThrow()).value();
    }

    private String canonicalUrl(String publicUrl) {
        return canonicalUrlFactory.canonicalUrl(new PublicUrl(publicUrl)).value();
    }

    private Stream<ContentItem> manageableContent() {
        return Stream.of(ContentStatus.DRAFT, ContentStatus.UNPUBLISHED, ContentStatus.PUBLISHED)
                .flatMap(status -> contentItems.findByStatus(status).stream());
    }

    private static String languageCode(ContentLanguage language) {
        return language.name().toLowerCase(Locale.ROOT);
    }

    private static String languageLabel(ContentLanguage language) {
        return switch (language) {
            case EN -> "English";
            case TR -> "Turkish";
        };
    }

    private record RenderableContent(ContentId contentId, ContentLanguage language, String title, String publicUrl) {
    }
}
