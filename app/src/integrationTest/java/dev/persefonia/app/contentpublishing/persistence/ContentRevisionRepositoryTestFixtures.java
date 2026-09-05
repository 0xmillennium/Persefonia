package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.revision.ChangeNote;
import dev.persefonia.contentpublishing.domain.revision.CompleteContentSnapshot;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionMetadata;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.revision.RevisionType;
import java.time.Instant;

final class ContentRevisionRepositoryTestFixtures {
    static final AdminIdentityRef ADMIN = AdminIdentityRef.newId();
    static final Instant NOW = Instant.parse("2026-06-11T10:00:00Z");

    private ContentRevisionRepositoryTestFixtures() {
    }

    static ContentRevision revision(ContentId contentId, int number, RevisionType type, String slug) {
        RenderedHtml html = type == RevisionType.PUBLISH
                ? RenderedHtml.sanitized("<article>" + slug + "</article>")
                : null;
        return ContentRevision.create(
                ContentRevisionId.newId(),
                contentId,
                RevisionNumber.of(number),
                type,
                snapshot(slug, html),
                ADMIN,
                NOW.plusSeconds(number),
                ChangeNote.of("Change " + number));
    }

    static CompleteContentSnapshot snapshot(String slug, RenderedHtml renderedHtml) {
        return CompleteContentSnapshot.of(
                Title.of("Revision " + slug),
                Slug.ofCanonical(slug),
                Summary.of("Revision summary " + slug),
                MarkdownSource.of("# Revision " + slug),
                renderedHtml,
                RevisionMetadata.from(ContentItemRepositoryTestFixtures.metadata(slug)));
    }
}
