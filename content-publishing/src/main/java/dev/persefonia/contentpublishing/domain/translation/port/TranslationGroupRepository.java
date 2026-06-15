package dev.persefonia.contentpublishing.domain.translation.port;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroup;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import java.util.Optional;

public interface TranslationGroupRepository {
    TranslationGroup save(TranslationGroup group);

    Optional<TranslationGroup> findById(TranslationGroupId id);

    Optional<TranslationGroup> findByContentItemId(ContentId contentItemId);

    boolean contentItemBelongsToAnyGroup(ContentId contentItemId);
}
