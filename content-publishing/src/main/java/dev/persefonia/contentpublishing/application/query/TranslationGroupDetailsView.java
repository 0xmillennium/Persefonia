package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import java.util.List;
import java.util.Objects;

public record TranslationGroupDetailsView(
        TranslationGroupId groupId,
        ContentType contentType,
        List<TranslationGroupEntryView> entries) {
    public TranslationGroupDetailsView {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(contentType, "contentType");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    public boolean canRemoveEntries() {
        return entries.size() > 1;
    }
}
