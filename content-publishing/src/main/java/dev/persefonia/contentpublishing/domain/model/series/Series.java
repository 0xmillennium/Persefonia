package dev.persefonia.contentpublishing.domain.model.series;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Series {
    private final SeriesId id;
    private final ContentLanguage language;
    private SeriesSlug slug;
    private SeriesTitle title;
    private SeriesDescription description;
    private SeriesStatus status;
    private final List<SeriesEntry> entries;
    private final Instant createdAt;
    private Instant updatedAt;
    private Version version;

    private Series(
            SeriesId id,
            ContentLanguage language,
            SeriesSlug slug,
            SeriesTitle title,
            SeriesDescription description,
            SeriesStatus status,
            List<SeriesEntry> entries,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        this.id = Objects.requireNonNull(id, "id");
        this.language = Objects.requireNonNull(language, "language");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.title = Objects.requireNonNull(title, "title");
        this.description = description;
        this.status = Objects.requireNonNull(status, "status");
        this.entries = new ArrayList<>(sortedAndValidatedEntries(Objects.requireNonNull(entries, "entries")));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = Objects.requireNonNull(version, "version");
        if (updatedAt.isBefore(createdAt)) {
            throw new SeriesValidationException("updatedAt must not be before createdAt");
        }
    }

    public static Series create(
            SeriesId id,
            ContentLanguage language,
            SeriesSlug slug,
            SeriesTitle title,
            SeriesDescription description,
            Instant now) {
        Objects.requireNonNull(now, "now");
        return new Series(id, language, slug, title, description, SeriesStatus.ACTIVE, List.of(), now, now, Version.initial());
    }

    public static Series rehydrate(
            SeriesId id,
            ContentLanguage language,
            SeriesSlug slug,
            SeriesTitle title,
            SeriesDescription description,
            SeriesStatus status,
            List<SeriesEntry> entries,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        return new Series(id, language, slug, title, description, status, entries, createdAt, updatedAt, version);
    }

    public void updateMetadata(
            SeriesTitle newTitle,
            SeriesSlug newSlug,
            SeriesDescription newDescription,
            Instant now) {
        rejectArchivedMutation();
        this.title = Objects.requireNonNull(newTitle, "newTitle");
        this.slug = Objects.requireNonNull(newSlug, "newSlug");
        this.description = newDescription;
        markUpdated(now);
    }

    public void archive(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == SeriesStatus.ARCHIVED) {
            return;
        }
        this.status = SeriesStatus.ARCHIVED;
        markUpdated(now);
    }

    public SeriesEntry addEntry(SeriesEntryId entryId, ContentId contentItemId, Instant now) {
        rejectArchivedMutation();
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(contentItemId, "contentItemId");
        if (containsContentItem(contentItemId)) {
            throw new SeriesValidationException("content item is already part of this series");
        }
        SeriesEntry entry = new SeriesEntry(
                entryId,
                contentItemId,
                SeriesEntryPosition.of(entries.size() + 1),
                Objects.requireNonNull(now, "now"));
        entries.add(entry);
        markUpdated(now);
        return entry;
    }

    public void removeEntry(SeriesEntryId entryId, Instant now) {
        rejectArchivedMutation();
        Objects.requireNonNull(entryId, "entryId");
        SeriesEntry existing = entries.stream()
                .filter(entry -> entry.id().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new SeriesValidationException("series entry does not exist"));
        entries.remove(existing);
        normalizePositions();
        markUpdated(now);
    }

    public void reorderEntries(List<SeriesEntryId> orderedEntryIds, Instant now) {
        rejectArchivedMutation();
        Objects.requireNonNull(orderedEntryIds, "orderedEntryIds");
        if (orderedEntryIds.size() != entries.size() || new HashSet<>(orderedEntryIds).size() != orderedEntryIds.size()) {
            throw new SeriesValidationException("reorder must include each series entry exactly once");
        }
        Map<SeriesEntryId, SeriesEntry> byId = entries.stream()
                .collect(Collectors.toMap(SeriesEntry::id, Function.identity()));
        List<SeriesEntry> reordered = new ArrayList<>();
        for (int index = 0; index < orderedEntryIds.size(); index++) {
            SeriesEntry existing = byId.get(orderedEntryIds.get(index));
            if (existing == null) {
                throw new SeriesValidationException("reorder must include each series entry exactly once");
            }
            reordered.add(existing.withPosition(SeriesEntryPosition.of(index + 1)));
        }
        entries.clear();
        entries.addAll(reordered);
        markUpdated(now);
    }

    public boolean containsContentItem(ContentId contentItemId) {
        return entries.stream().anyMatch(entry -> entry.contentItemId().equals(contentItemId));
    }

    public boolean isArchived() {
        return status == SeriesStatus.ARCHIVED;
    }

    private void rejectArchivedMutation() {
        if (status == SeriesStatus.ARCHIVED) {
            throw new SeriesValidationException("archived series cannot be mutated");
        }
    }

    private void normalizePositions() {
        for (int index = 0; index < entries.size(); index++) {
            entries.set(index, entries.get(index).withPosition(SeriesEntryPosition.of(index + 1)));
        }
    }

    private void markUpdated(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
        this.version = version.next();
    }

    private static List<SeriesEntry> sortedAndValidatedEntries(List<SeriesEntry> entries) {
        Set<ContentId> contentItemIds = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (SeriesEntry entry : entries) {
            if (!contentItemIds.add(entry.contentItemId())) {
                throw new SeriesValidationException("series contains duplicate content item");
            }
            if (!positions.add(entry.position().value())) {
                throw new SeriesValidationException("series contains duplicate entry position");
            }
        }
        List<SeriesEntry> sorted = entries.stream()
                .sorted(Comparator.comparingInt(entry -> entry.position().value()))
                .toList();
        for (int index = 0; index < sorted.size(); index++) {
            int expected = index + 1;
            if (sorted.get(index).position().value() != expected) {
                throw new SeriesValidationException("series entry positions must be contiguous");
            }
        }
        return sorted;
    }

    public SeriesId id() {
        return id;
    }

    public ContentLanguage language() {
        return language;
    }

    public SeriesSlug slug() {
        return slug;
    }

    public SeriesTitle title() {
        return title;
    }

    public java.util.Optional<SeriesDescription> description() {
        return java.util.Optional.ofNullable(description);
    }

    public SeriesStatus status() {
        return status;
    }

    public List<SeriesEntry> entries() {
        return List.copyOf(entries);
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
