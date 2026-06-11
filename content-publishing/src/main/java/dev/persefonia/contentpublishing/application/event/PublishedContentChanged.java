package dev.persefonia.contentpublishing.application.event;

import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import java.time.Instant;

public record PublishedContentChanged(
        ContentId contentId,
        ContentType type,
        ContentLanguage language,
        AdminIdentityRef actor,
        Instant occurredAt,
        RevisionNumber revisionNumber)
        implements ContentPublishingEvent {
}
