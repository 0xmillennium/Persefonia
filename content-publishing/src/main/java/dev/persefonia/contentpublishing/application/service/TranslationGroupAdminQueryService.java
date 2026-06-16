package dev.persefonia.contentpublishing.application.service;

import static dev.persefonia.contentpublishing.application.service.ContentApplicationSupport.requiredContent;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.port.TranslationCandidateContentReadModel;
import dev.persefonia.contentpublishing.application.query.ContentTranslationSectionView;
import dev.persefonia.contentpublishing.application.query.TranslationCandidateItem;
import dev.persefonia.contentpublishing.application.query.TranslationGroupDetailsView;
import dev.persefonia.contentpublishing.application.query.TranslationGroupEntryView;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroup;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntry;
import dev.persefonia.contentpublishing.domain.translation.port.TranslationGroupRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TranslationGroupAdminQueryService {
    private final ContentItemRepository contentItems;
    private final TranslationGroupRepository translationGroups;
    private final TranslationCandidateContentReadModel candidateReadModel;
    private final ContentCommandAuthorizationPolicy authorization;

    public TranslationGroupAdminQueryService(
            ContentItemRepository contentItems,
            TranslationGroupRepository translationGroups,
            TranslationCandidateContentReadModel candidateReadModel,
            ContentCommandAuthorizationPolicy authorization) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
        this.translationGroups = Objects.requireNonNull(translationGroups, "translationGroups");
        this.candidateReadModel = Objects.requireNonNull(candidateReadModel, "candidateReadModel");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public ContentTranslationSectionView loadSection(ContentCommandActor actor, ContentId contentId) {
        authorization.requireOwner(actor, "translation-group.admin-view");
        ContentItem item = requiredContent(contentItems, contentId);
        Optional<TranslationGroup> group = translationGroups.findByContentItemId(contentId);
        if (group.isEmpty()) {
            return new ContentTranslationSectionView(contentId, item.type(), Optional.empty(), List.of());
        }
        TranslationGroupDetailsView details = details(group.get());
        List<TranslationCandidateItem> candidates = candidatesFor(group.get());
        return new ContentTranslationSectionView(contentId, item.type(), Optional.of(details), candidates);
    }

    private TranslationGroupDetailsView details(TranslationGroup group) {
        List<TranslationGroupEntryView> entries = group.entries().stream()
                .map(this::entryView)
                .toList();
        return new TranslationGroupDetailsView(group.id(), group.contentType(), entries);
    }

    private TranslationGroupEntryView entryView(TranslationGroupEntry entry) {
        Optional<String> title = contentItems.findById(entry.contentItemId())
                .flatMap(ContentItem::title)
                .map(Title::value);
        return new TranslationGroupEntryView(entry.id(), entry.contentItemId(), entry.language(), title);
    }

    private List<TranslationCandidateItem> candidatesFor(TranslationGroup group) {
        return candidateReadModel.candidatesFor(group.id(), group.contentType());
    }
}
