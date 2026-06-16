package dev.persefonia.contentpublishing.domain.translation;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TranslationGroup {
    private final TranslationGroupId id;
    private final List<TranslationGroupEntry> entries;
    private final Instant createdAt;
    private Instant updatedAt;
    private Version version;

    private TranslationGroup(
            TranslationGroupId id,
            List<TranslationGroupEntry> entries,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        this.id = Objects.requireNonNull(id, "id");
        this.entries = new ArrayList<>(Objects.requireNonNull(entries, "entries"));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = Objects.requireNonNull(version, "version");

        if (this.entries.isEmpty()) {
            throw new TranslationGroupValidationException("translation group must have at least one entry");
        }
        validateEntries(this.entries);
        if (updatedAt.isBefore(createdAt)) {
            throw new TranslationGroupValidationException("updatedAt must not be before createdAt");
        }
    }

    public static TranslationGroup create(TranslationGroupId id, TranslationGroupEntry initialEntry, Instant now) {
        Objects.requireNonNull(initialEntry, "initialEntry");
        Objects.requireNonNull(now, "now");
        return new TranslationGroup(id, List.of(initialEntry), now, now, Version.initial());
    }

    public static TranslationGroup rehydrate(
            TranslationGroupId id,
            List<TranslationGroupEntry> entries,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        return new TranslationGroup(id, entries, createdAt, updatedAt, version);
    }

    private static void validateEntries(List<TranslationGroupEntry> entries) {
        Set<ContentId> contentItemIds = new HashSet<>();
        Set<ContentLanguage> languages = new HashSet<>();
        ContentType contentType = entries.getFirst().contentType();
        for (TranslationGroupEntry entry : entries) {
            if (!contentItemIds.add(entry.contentItemId())) {
                throw new TranslationGroupValidationException("translation group contains duplicate content item");
            }
            if (!languages.add(entry.language())) {
                throw new TranslationGroupValidationException("translation group contains duplicate language");
            }
            if (entry.contentType() != contentType) {
                throw new TranslationGroupValidationException("translation group entries must share the same content type");
            }
        }
    }

    public void addEntry(TranslationGroupEntry entry, Instant now) {
        Objects.requireNonNull(entry, "entry");
        if (containsContentItem(entry.contentItemId())) {
            throw new TranslationGroupValidationException("content item is already part of this translation group");
        }
        if (containsLanguage(entry.language())) {
            throw new TranslationGroupValidationException("translation group already contains language " + entry.language());
        }
        if (entry.contentType() != contentType()) {
            throw new TranslationGroupValidationException("translation group entries must share the same content type");
        }
        entries.add(entry);
        markUpdated(now);
    }

    public void removeEntry(TranslationGroupEntryId entryId, Instant now) {
        Objects.requireNonNull(entryId, "entryId");
        TranslationGroupEntry existing = entries.stream()
                .filter(entry -> entry.id().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new TranslationGroupValidationException(
                        "translation group does not contain entry " + entryId.value()));
        if (entries.size() == 1) {
            throw new TranslationGroupValidationException("translation group must keep at least one entry");
        }
        entries.remove(existing);
        markUpdated(now);
    }

    public boolean containsLanguage(ContentLanguage language) {
        return entries.stream().anyMatch(entry -> entry.language() == language);
    }

    public boolean containsContentItem(ContentId contentItemId) {
        return entries.stream().anyMatch(entry -> entry.contentItemId().equals(contentItemId));
    }

    public ContentType contentType() {
        return entries.getFirst().contentType();
    }

    private void markUpdated(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
        this.version = version.next();
    }

    public TranslationGroupId id() {
        return id;
    }

    public List<TranslationGroupEntry> entries() {
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
