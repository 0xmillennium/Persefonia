package dev.persefonia.contentpublishing.application.event;

import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import java.time.Instant;

public record ContentVisibilityChanged(
        ContentId contentId,
        ContentType type,
        ContentLanguage language,
        AdminIdentityRef actor,
        Instant occurredAt,
        ContentVisibility oldVisibility,
        ContentVisibility newVisibility)
        implements ContentPublishingEvent {
}
