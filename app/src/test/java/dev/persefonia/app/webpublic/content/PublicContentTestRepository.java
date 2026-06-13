package dev.persefonia.app.webpublic.content;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PublicContentTestRepository implements ContentItemRepository {
    private final Map<ContentId, ContentItem> items = new LinkedHashMap<>();

    @Override
    public ContentItem save(ContentItem item) {
        items.put(item.id(), item);
        return item;
    }

    @Override
    public Optional<ContentItem> findById(ContentId id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public Optional<ContentItem> findBySlugAndTypeAndLanguage(Slug slug, ContentType type, ContentLanguage language) {
        return items.values().stream()
                .filter(item -> item.type() == type
                        && item.language() == language
                        && item.slug().filter(slug::equals).isPresent())
                .findFirst();
    }

    @Override
    public Optional<ContentItem> findPublishedByRoute(ContentType type, Slug slug, ContentLanguage language) {
        return findBySlugAndTypeAndLanguage(slug, type, language).filter(ContentItem::isPublished);
    }

    @Override
    public List<ContentItem> findDrafts() {
        return findByStatus(ContentStatus.DRAFT);
    }

    @Override
    public List<ContentItem> findByStatus(ContentStatus status) {
        return items.values().stream().filter(item -> item.status() == status).toList();
    }

    @Override
    public boolean existsSlugInNamespace(ContentType type, ContentLanguage language, Slug slug) {
        return findBySlugAndTypeAndLanguage(slug, type, language).isPresent();
    }

    public void add(ContentItem item) {
        items.put(item.id(), item);
    }

    public void reset() {
        items.clear();
    }
}
