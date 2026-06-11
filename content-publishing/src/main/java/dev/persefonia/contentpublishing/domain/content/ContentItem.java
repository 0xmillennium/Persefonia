package dev.persefonia.contentpublishing.domain.content;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ContentItem {
    private final ContentId id;
    private final ContentType type;
    private ContentStatus status;
    private ContentVisibility visibility;
    private final ContentLanguage language;
    private Slug slug;
    private Title title;
    private Summary summary;
    private MarkdownSource markdownSource;
    private ContentMetadata metadata;
    private ContentRenderSnapshot renderSnapshot;
    private Set<TagId> tagIds;
    private Instant publishedAt;
    private Instant unpublishedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private Version version;

    private ContentItem(
            ContentId id,
            ContentType type,
            ContentStatus status,
            ContentVisibility visibility,
            ContentLanguage language,
            Slug slug,
            Title title,
            Summary summary,
            MarkdownSource markdownSource,
            ContentMetadata metadata,
            ContentRenderSnapshot renderSnapshot,
            Set<TagId> tagIds,
            Instant publishedAt,
            Instant unpublishedAt,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.status = Objects.requireNonNull(status, "status");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.language = Objects.requireNonNull(language, "language");
        this.slug = slug;
        this.title = title;
        this.summary = summary;
        this.markdownSource = markdownSource;
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.renderSnapshot = renderSnapshot;
        this.tagIds = Set.copyOf(Objects.requireNonNull(tagIds, "tagIds"));
        this.publishedAt = publishedAt;
        this.unpublishedAt = unpublishedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = Objects.requireNonNull(version, "version");

        if (updatedAt.isBefore(createdAt)) {
            throw new ContentValidationException("updatedAt must not be before createdAt");
        }
    }

    public static ContentItem createDraft(
            ContentId id,
            ContentType type,
            ContentVisibility visibility,
            ContentLanguage language,
            Instant now) {
        Objects.requireNonNull(now, "now");
        return new ContentItem(
                id,
                type,
                ContentStatus.DRAFT,
                visibility,
                language,
                null,
                null,
                null,
                null,
                ContentMetadata.empty(),
                null,
                Set.of(),
                null,
                null,
                now,
                now,
                Version.initial());
    }

    public void changeTitle(Title title, Instant now) {
        rejectArchivedEdit();
        this.title = Objects.requireNonNull(title, "title");
        markUpdated(now);
    }

    public void clearTitle(Instant now) {
        rejectArchivedEdit();
        this.title = null;
        markUpdated(now);
    }

    public void changeSlug(Slug slug, Instant now) {
        rejectArchivedEdit();
        this.slug = Objects.requireNonNull(slug, "slug");
        markUpdated(now);
    }

    public void clearSlug(Instant now) {
        rejectArchivedEdit();
        this.slug = null;
        markUpdated(now);
    }

    public void changeSummary(Summary summary, Instant now) {
        rejectArchivedEdit();
        this.summary = Objects.requireNonNull(summary, "summary");
        markUpdated(now);
    }

    public void clearSummary(Instant now) {
        rejectArchivedEdit();
        this.summary = null;
        markUpdated(now);
    }

    public void changeMarkdownSource(MarkdownSource markdownSource, Instant now) {
        rejectArchivedEdit();
        this.markdownSource = Objects.requireNonNull(markdownSource, "markdownSource");
        markUpdated(now);
    }

    public void clearMarkdownSource(Instant now) {
        rejectArchivedEdit();
        this.markdownSource = null;
        markUpdated(now);
    }

    public void changeMetadata(ContentMetadata metadata, Instant now) {
        rejectArchivedEdit();
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        markUpdated(now);
    }

    public void changeVisibility(ContentVisibility visibility, Instant now) {
        rejectArchivedEdit();
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        markUpdated(now);
    }

    public void replaceTags(Set<TagId> tagIds, Instant now) {
        rejectArchivedEdit();
        this.tagIds = Set.copyOf(Objects.requireNonNull(tagIds, "tagIds"));
        markUpdated(now);
    }

    public void publish(ContentRenderSnapshot renderSnapshot, Instant now) {
        if (isArchived()) {
            throw new ContentLifecycleException("archived content cannot be published");
        }
        Objects.requireNonNull(now, "now");
        if (renderSnapshot == null) {
            throw new ContentValidationException("content must have render snapshot before publishing");
        }
        requirePublishableContent();
        this.renderSnapshot = renderSnapshot;
        this.status = ContentStatus.PUBLISHED;
        if (publishedAt == null) {
            this.publishedAt = now;
        }
        this.unpublishedAt = null;
        markUpdated(now);
    }

    public void unpublish(Instant now) {
        if (!isPublished()) {
            throw new ContentLifecycleException("only published content can be unpublished");
        }
        Objects.requireNonNull(now, "now");
        this.status = ContentStatus.UNPUBLISHED;
        this.unpublishedAt = now;
        markUpdated(now);
    }

    public void archive(Instant now) {
        Objects.requireNonNull(now, "now");
        if (isPublished() && unpublishedAt == null) {
            unpublishedAt = now;
        }
        this.status = ContentStatus.ARCHIVED;
        markUpdated(now);
    }

    private void rejectArchivedEdit() {
        if (isArchived()) {
            throw new ContentLifecycleException("archived content cannot be edited");
        }
    }

    private void requirePublishableContent() {
        if (slug == null) {
            throw new ContentValidationException("content must have slug before publishing");
        }
        if (title == null) {
            throw new ContentValidationException("content must have title before publishing");
        }
        if (summary == null) {
            throw new ContentValidationException("content must have summary before publishing");
        }
        if (markdownSource == null) {
            throw new ContentValidationException("content must have markdown source before publishing");
        }
        if (metadata.canonicalPath().isEmpty()) {
            throw new ContentValidationException("content metadata must have canonical path before publishing");
        }
    }

    private void markUpdated(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
        this.version = version.next();
    }

    public boolean isDraft() {
        return status == ContentStatus.DRAFT;
    }

    public boolean isPublished() {
        return status == ContentStatus.PUBLISHED;
    }

    public boolean isUnpublished() {
        return status == ContentStatus.UNPUBLISHED;
    }

    public boolean isArchived() {
        return status == ContentStatus.ARCHIVED;
    }

    public boolean isPubliclyRenderable() {
        return isPublished() && visibility != ContentVisibility.PRIVATE;
    }

    public boolean isListedPublicly() {
        return isPublished() && visibility == ContentVisibility.PUBLIC;
    }

    public boolean isDirectUrlEligible() {
        return isPublished() && visibility != ContentVisibility.PRIVATE;
    }

    public ContentId id() {
        return id;
    }

    public ContentType type() {
        return type;
    }

    public ContentStatus status() {
        return status;
    }

    public ContentVisibility visibility() {
        return visibility;
    }

    public ContentLanguage language() {
        return language;
    }

    public Optional<Slug> slug() {
        return Optional.ofNullable(slug);
    }

    public Optional<Title> title() {
        return Optional.ofNullable(title);
    }

    public Optional<Summary> summary() {
        return Optional.ofNullable(summary);
    }

    public Optional<MarkdownSource> markdownSource() {
        return Optional.ofNullable(markdownSource);
    }

    public ContentMetadata metadata() {
        return metadata;
    }

    public Optional<ContentRenderSnapshot> renderSnapshot() {
        return Optional.ofNullable(renderSnapshot);
    }

    public Set<TagId> tagIds() {
        return Set.copyOf(tagIds);
    }

    public Optional<Instant> publishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    public Optional<Instant> unpublishedAt() {
        return Optional.ofNullable(unpublishedAt);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Version version() {
        return version;
    }
}
