package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import java.util.Objects;
import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.Optional;

public record TranslationGroupResult(
        TranslationGroupId translationGroupId,
        ContentId contentItemId,
        boolean publicMembershipChanged,
        Optional<PublicUrl> removedPublicRoute) {
    public TranslationGroupResult(TranslationGroupId translationGroupId) {
        this(translationGroupId, null, false, Optional.empty());
    }

    public TranslationGroupResult(TranslationGroupId translationGroupId, ContentId contentItemId) {
        this(translationGroupId, contentItemId, false, Optional.empty());
    }

    public TranslationGroupResult {
        Objects.requireNonNull(translationGroupId, "translationGroupId");
        removedPublicRoute = Objects.requireNonNull(removedPublicRoute, "removedPublicRoute");
    }
}
