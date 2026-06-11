package dev.persefonia.contentpublishing.domain.support;

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
import java.time.Instant;

public final class ContentRevisionTestFixtures {
    public static final Instant CREATED_AT = Instant.parse("2026-06-10T11:00:00Z");

    private ContentRevisionTestFixtures() {
    }

    public static ContentRevision publishRevision() {
        return ContentRevision.publishSnapshot(
                ContentRevisionId.newId(),
                ContentId.newId(),
                RevisionNumber.of(1),
                completeSnapshot(),
                AdminIdentityRef.newId(),
                CREATED_AT,
                ChangeNote.of("Initial publish"));
    }

    public static CompleteContentSnapshot completeSnapshot() {
        return CompleteContentSnapshot.of(
                title(),
                slug(),
                summary(),
                markdownSource(),
                renderedHtml(),
                RevisionMetadata.from(ContentItemTestFixtures.metadataWithCanonicalPath()));
    }

    public static CompleteContentSnapshot sourceOnlySnapshot() {
        return CompleteContentSnapshot.of(
                title(),
                slug(),
                summary(),
                markdownSource(),
                null,
                RevisionMetadata.from(ContentItemTestFixtures.metadataWithCanonicalPath()));
    }

    public static Title title() {
        return Title.of("Revision title");
    }

    public static Slug slug() {
        return Slug.ofCanonical("revision-title");
    }

    public static Summary summary() {
        return Summary.of("Revision summary.");
    }

    public static MarkdownSource markdownSource() {
        return MarkdownSource.of("# Revision title");
    }

    public static RenderedHtml renderedHtml() {
        return RenderedHtml.sanitized("<h1>Revision title</h1>");
    }
}
