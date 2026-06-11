package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.revision.ChangeNote;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.revision.RevisionType;
import java.time.Instant;
import java.util.Optional;

public record ContentRevisionResult(
        ContentRevisionId revisionId,
        ContentId contentId,
        RevisionNumber revisionNumber,
        RevisionType revisionType,
        Optional<RenderedHtml> renderedHtml,
        AdminIdentityRef createdBy,
        Instant createdAt,
        Optional<ChangeNote> changeNote) {
}
