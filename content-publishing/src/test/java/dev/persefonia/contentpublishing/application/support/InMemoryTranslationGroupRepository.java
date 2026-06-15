package dev.persefonia.contentpublishing.application.support;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroup;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import dev.persefonia.contentpublishing.domain.translation.port.TranslationGroupRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryTranslationGroupRepository implements TranslationGroupRepository {
    private final Map<TranslationGroupId, TranslationGroup> groups = new LinkedHashMap<>();
    private int saveCount;

    @Override
    public TranslationGroup save(TranslationGroup group) {
        saveCount++;
        groups.put(group.id(), group);
        return group;
    }

    @Override
    public Optional<TranslationGroup> findById(TranslationGroupId id) {
        return Optional.ofNullable(groups.get(id));
    }

    @Override
    public Optional<TranslationGroup> findByContentItemId(ContentId contentItemId) {
        return groups.values().stream()
                .filter(group -> group.containsContentItem(contentItemId))
                .findFirst();
    }

    @Override
    public boolean contentItemBelongsToAnyGroup(ContentId contentItemId) {
        return findByContentItemId(contentItemId).isPresent();
    }

    public void add(TranslationGroup group) {
        groups.put(group.id(), group);
    }

    public int saveCount() {
        return saveCount;
    }
}
