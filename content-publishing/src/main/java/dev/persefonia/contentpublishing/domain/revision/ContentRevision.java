package dev.persefonia.contentpublishing.domain.revision;

import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentValidationException;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class ContentRevision {
    private final ContentRevisionId id;
    private final ContentId contentId;
    private final RevisionNumber revisionNumber;
    private final RevisionType revisionType;
    private final Title title;
    private final Slug slug;
    private final Summary summary;
    private final MarkdownSource markdownSource;
    private final RenderedHtml renderedHtml;
    private final RevisionMetadata metadata;
    private final AdminIdentityRef createdBy;
    private final Instant createdAt;
    private final ChangeNote changeNote;

    private ContentRevision(
            ContentRevisionId id,
            ContentId contentId,
            RevisionNumber revisionNumber,
            RevisionType revisionType,
            CompleteContentSnapshot snapshot,
            AdminIdentityRef createdBy,
            Instant createdAt,
            ChangeNote changeNote) {
        this.id = Objects.requireNonNull(id, "id");
        this.contentId = Objects.requireNonNull(contentId, "contentId");
        this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber");
        this.revisionType = Objects.requireNonNull(revisionType, "revisionType");
        Objects.requireNonNull(snapshot, "snapshot");
        if (revisionType == RevisionType.PUBLISH && snapshot.renderedHtml().isEmpty()) {
            throw new ContentValidationException("publish revision must include rendered html");
        }
        this.title = snapshot.title();
        this.slug = snapshot.slug();
        this.summary = snapshot.summary();
        this.markdownSource = snapshot.markdownSource();
        this.renderedHtml = snapshot.renderedHtml().orElse(null);
        this.metadata = snapshot.metadata();
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.changeNote = changeNote;
    }

    public static ContentRevision create(
            ContentRevisionId id,
            ContentId contentId,
            RevisionNumber revisionNumber,
            RevisionType revisionType,
            CompleteContentSnapshot snapshot,
            AdminIdentityRef createdBy,
            Instant createdAt,
            ChangeNote changeNote) {
        return new ContentRevision(id, contentId, revisionNumber, revisionType, snapshot, createdBy, createdAt, changeNote);
    }

    public static ContentRevision publishSnapshot(
            ContentRevisionId id,
            ContentId contentId,
            RevisionNumber revisionNumber,
            CompleteContentSnapshot snapshot,
            AdminIdentityRef createdBy,
            Instant createdAt,
            ChangeNote changeNote) {
        return create(id, contentId, revisionNumber, RevisionType.PUBLISH, snapshot, createdBy, createdAt, changeNote);
    }

    public static ContentRevision manualSnapshot(
            ContentRevisionId id,
            ContentId contentId,
            RevisionNumber revisionNumber,
            CompleteContentSnapshot snapshot,
            AdminIdentityRef createdBy,
            Instant createdAt,
            ChangeNote changeNote) {
        return create(id, contentId, revisionNumber, RevisionType.MANUAL_SNAPSHOT, snapshot, createdBy, createdAt, changeNote);
    }

    public static ContentRevision restoreSourceSnapshot(
            ContentRevisionId id,
            ContentId contentId,
            RevisionNumber revisionNumber,
            CompleteContentSnapshot snapshot,
            AdminIdentityRef createdBy,
            Instant createdAt,
            ChangeNote changeNote) {
        return create(id, contentId, revisionNumber, RevisionType.RESTORE_SOURCE, snapshot, createdBy, createdAt, changeNote);
    }

    public ContentRevisionId id() {
        return id;
    }

    public ContentId contentId() {
        return contentId;
    }

    public RevisionNumber revisionNumber() {
        return revisionNumber;
    }

    public RevisionType revisionType() {
        return revisionType;
    }

    public Title title() {
        return title;
    }

    public Slug slug() {
        return slug;
    }

    public Summary summary() {
        return summary;
    }

    public MarkdownSource markdownSource() {
        return markdownSource;
    }

    public Optional<RenderedHtml> renderedHtml() {
        return Optional.ofNullable(renderedHtml);
    }

    public RevisionMetadata metadata() {
        return metadata;
    }

    public AdminIdentityRef createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<ChangeNote> changeNote() {
        return Optional.ofNullable(changeNote);
    }
}
