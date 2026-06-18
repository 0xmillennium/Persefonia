package dev.persefonia.profileportfolio.domain.cv;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import dev.persefonia.profileportfolio.domain.common.Version;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ActiveCvProfile {
    private final ActiveCvProfileId id;
    private final Instant createdAt;
    private Instant updatedAt;
    private Version version;
    private final Map<ContentLanguage, ActiveCvDocument> documents;

    private ActiveCvProfile(
            ActiveCvProfileId id,
            Collection<ActiveCvDocument> documents,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        this.id = Objects.requireNonNull(id, "id");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = Objects.requireNonNull(version, "version");
        this.documents = new LinkedHashMap<>();
        for (ActiveCvDocument document : Objects.requireNonNull(documents, "documents")) {
            ActiveCvDocument previous = this.documents.put(
                    Objects.requireNonNull(document, "document").language(), document);
            if (previous != null) {
                throw new PortfolioValidationException("Active CV document language must be unique");
            }
        }
    }

    public static ActiveCvProfile rehydrate(
            ActiveCvProfileId id,
            Collection<ActiveCvDocument> documents,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        return new ActiveCvProfile(id, documents, createdAt, updatedAt, version);
    }

    public void selectDocument(
            ContentLanguage language,
            MediaAssetId mediaAssetId,
            CvDisplayLabel displayLabel,
            Instant now) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(mediaAssetId, "mediaAssetId");
        Objects.requireNonNull(now, "now");
        ActiveCvDocument current = documents.get(language);
        documents.put(language, current == null
                ? ActiveCvDocument.select(language, mediaAssetId, displayLabel, now)
                : current.replace(mediaAssetId, displayLabel, now));
        markUpdated(now);
    }

    public void removeDocument(ContentLanguage language, Instant now) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(now, "now");
        if (documents.remove(language) != null) {
            markUpdated(now);
        }
    }

    public Optional<ActiveCvDocument> documentFor(ContentLanguage language) {
        return Optional.ofNullable(documents.get(Objects.requireNonNull(language, "language")));
    }

    public List<ActiveCvDocument> documents() {
        return List.copyOf(documents.values());
    }

    private void markUpdated(Instant now) {
        updatedAt = now;
        version = version.next();
    }

    public ActiveCvProfileId id() {
        return id;
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
