package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.application.query.TranslationCandidateItem;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import java.util.List;

public interface TranslationCandidateContentReadModel {
    List<TranslationCandidateItem> candidatesFor(TranslationGroupId groupId, ContentType contentType);
}
