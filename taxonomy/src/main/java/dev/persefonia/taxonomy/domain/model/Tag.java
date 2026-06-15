package dev.persefonia.taxonomy.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class Tag {
    private final TagId id;
    private TagName name;
    private NormalizedTagName normalizedName;
    private TagSlug slug;
    private TagDescription description;
    private TagStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private Tag(
            TagId id,
            TagName name,
            NormalizedTagName normalizedName,
            TagSlug slug,
            TagDescription description,
            TagStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.normalizedName = Objects.requireNonNull(normalizedName, "normalizedName");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.description = Objects.requireNonNull(description, "description");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new TagValidationException("updatedAt must not be before createdAt");
        }
        if (version < 0) {
            throw new TagValidationException("version must not be negative");
        }
        this.version = version;
    }

    public static Tag create(
            TagId id,
            TagName name,
            NormalizedTagName normalizedName,
            TagSlug slug,
            TagDescription description,
            Instant now) {
        return new Tag(id, name, normalizedName, slug, description, TagStatus.ACTIVE, now, now, 0);
    }

    public static Tag rehydrate(
            TagId id,
            TagName name,
            NormalizedTagName normalizedName,
            TagSlug slug,
            TagDescription description,
            TagStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        return new Tag(id, name, normalizedName, slug, description, status, createdAt, updatedAt, version);
    }

    public void update(
            TagName name,
            NormalizedTagName normalizedName,
            TagSlug slug,
            TagDescription description,
            Instant now) {
        if (isArchived()) {
            throw new TagLifecycleException("archived tag cannot be edited");
        }
        this.name = Objects.requireNonNull(name, "name");
        this.normalizedName = Objects.requireNonNull(normalizedName, "normalizedName");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.description = Objects.requireNonNull(description, "description");
        markUpdated(now);
    }

    public void archive(Instant now) {
        if (isArchived()) {
            return;
        }
        status = TagStatus.ARCHIVED;
        markUpdated(now);
    }

    private void markUpdated(Instant now) {
        updatedAt = Objects.requireNonNull(now, "now");
        if (updatedAt.isBefore(createdAt)) {
            throw new TagValidationException("updatedAt must not be before createdAt");
        }
        version++;
    }

    public boolean isArchived() {
        return status == TagStatus.ARCHIVED;
    }

    public TagId id() { return id; }
    public TagName name() { return name; }
    public NormalizedTagName normalizedName() { return normalizedName; }
    public TagSlug slug() { return slug; }
    public TagDescription description() { return description; }
    public TagStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
