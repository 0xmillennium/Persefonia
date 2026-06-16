package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.discovery.ConfiguredContentCanonicalUrlFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.query.PublicContentPageResult;
import dev.persefonia.contentpublishing.application.query.PublicHreflangLink;
import dev.persefonia.contentpublishing.application.query.PublicTranslationLink;
import dev.persefonia.contentpublishing.application.query.PublicTranslationLinkSet;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntry;
import dev.persefonia.contentpublishing.domain.translation.port.TranslationGroupRepository;
import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class PublicTranslationLinkQueryService {
    private final ContentItemRepository items;
    private final TranslationGroupRepository translationGroups;
    private final ContentPublicRouteFactory routeFactory;
    private final ConfiguredContentCanonicalUrlFactory canonicalUrlFactory;

    public PublicTranslationLinkQueryService(
            ContentItemRepository items,
            TranslationGroupRepository translationGroups,
            ContentPublicRouteFactory routeFactory,
            ConfiguredContentCanonicalUrlFactory canonicalUrlFactory) {
        this.items = Objects.requireNonNull(items, "items");
        this.translationGroups = Objects.requireNonNull(translationGroups, "translationGroups");
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory");
    }

    public PublicTranslationLinkSet linksFor(PublicContentPageResult currentPage) {
        Objects.requireNonNull(currentPage, "currentPage");
        if (currentPage.visibility() != ContentVisibility.PUBLIC) {
            return PublicTranslationLinkSet.empty();
        }

        Optional<RenderablePublicContent> current = items.findById(currentPage.contentId())
                .flatMap(this::toRenderablePublicContent)
                .filter(candidate -> candidate.contentId().equals(currentPage.contentId()))
                .filter(candidate -> candidate.language() == currentPage.language())
                .filter(candidate -> candidate.publicUrl().equals(currentPage.canonicalPath().value()));
        if (current.isEmpty()) {
            return PublicTranslationLinkSet.empty();
        }

        return translationGroups.findByContentItemId(currentPage.contentId())
                .map(group -> linkSet(current.orElseThrow(), group.entries()))
                .orElseGet(PublicTranslationLinkSet::empty);
    }

    private PublicTranslationLinkSet linkSet(
            RenderablePublicContent current,
            List<TranslationGroupEntry> entries) {
        List<PublicTranslationLink> visibleLinks = new ArrayList<>();
        List<PublicHreflangLink> hreflangLinks = new ArrayList<>();

        for (TranslationGroupEntry entry : entries) {
            if (entry.contentItemId().equals(current.contentId())) {
                continue;
            }
            items.findById(entry.contentItemId())
                    .flatMap(this::toRenderablePublicContent)
                    .map(this::visibleLink)
                    .ifPresent(visibleLinks::add);
        }

        if (visibleLinks.isEmpty()) {
            return PublicTranslationLinkSet.empty();
        }

        hreflangLinks.add(new PublicHreflangLink(current.languageCode(), current.canonicalUrl()));
        visibleLinks.stream()
                .map(link -> new PublicHreflangLink(link.language(), link.canonicalUrl()))
                .forEach(hreflangLinks::add);
        return PublicTranslationLinkSet.withAlternates(visibleLinks, hreflangLinks);
    }

    private Optional<RenderablePublicContent> toRenderablePublicContent(ContentItem item) {
        if (item.status() != ContentStatus.PUBLISHED
                || item.visibility() != ContentVisibility.PUBLIC
                || item.renderSnapshot().isEmpty()) {
            return Optional.empty();
        }
        return item.slug().flatMap(slug ->
                item.title().flatMap(title ->
                        item.metadata().canonicalPath().flatMap(canonicalPath -> {
                            String publicUrl = publicUrl(item, slug);
                            if (!canonicalPath.value().equals(publicUrl)) {
                                return Optional.empty();
                            }
                            String canonicalUrl = canonicalUrl(publicUrl);
                            return Optional.of(new RenderablePublicContent(
                                    item.id(),
                                    item.language(),
                                    languageCode(item.language()),
                                    languageLabel(item.language()),
                                    title.value(),
                                    publicUrl,
                                    canonicalUrl));
                        })));
    }

    private PublicTranslationLink visibleLink(RenderablePublicContent content) {
        return new PublicTranslationLink(
                content.languageCode(),
                content.label(),
                content.title(),
                content.publicUrl(),
                content.canonicalUrl());
    }

    private String publicUrl(ContentItem item, Slug slug) {
        return routeFactory.publicUrl(item.type(), item.language(), slug).value();
    }

    private String canonicalUrl(String publicUrl) {
        return canonicalUrlFactory.canonicalUrl(new PublicUrl(publicUrl)).value();
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

    private record RenderablePublicContent(
            ContentId contentId,
            ContentLanguage language,
            String languageCode,
            String label,
            String title,
            String publicUrl,
            String canonicalUrl) {
    }
}
