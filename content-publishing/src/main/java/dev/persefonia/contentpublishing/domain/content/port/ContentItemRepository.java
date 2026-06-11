package dev.persefonia.contentpublishing.domain.content.port;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Slug;
import java.util.List;
import java.util.Optional;

public interface ContentItemRepository {
    ContentItem save(ContentItem item);

    Optional<ContentItem> findById(ContentId id);

    Optional<ContentItem> findBySlugAndTypeAndLanguage(
            Slug slug,
            ContentType type,
            ContentLanguage language);

    Optional<ContentItem> findPublishedByRoute(
            ContentType type,
            Slug slug,
            ContentLanguage language);

    List<ContentItem> findDrafts();

    List<ContentItem> findByStatus(ContentStatus status);

    boolean existsSlugInNamespace(
            ContentType type,
            ContentLanguage language,
            Slug slug);
}
