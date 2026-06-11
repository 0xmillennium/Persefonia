package dev.persefonia.contentpublishing.application.event;

import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import java.time.Instant;

public record ContentUnpublished(
        ContentId contentId,
        ContentType type,
        ContentLanguage language,
        AdminIdentityRef actor,
        Instant occurredAt,
        Instant unpublishedAt)
        implements ContentPublishingEvent {
}
