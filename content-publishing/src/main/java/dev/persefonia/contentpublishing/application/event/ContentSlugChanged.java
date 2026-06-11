package dev.persefonia.contentpublishing.application.event;

import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Slug;
import java.time.Instant;

public record ContentSlugChanged(
        ContentId contentId,
        ContentType type,
        ContentLanguage language,
        AdminIdentityRef actor,
        Instant occurredAt,
        Slug oldSlug,
        Slug newSlug)
        implements ContentPublishingEvent {
}
