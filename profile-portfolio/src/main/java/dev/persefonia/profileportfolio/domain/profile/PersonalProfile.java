package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.common.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class PersonalProfile {
    private final ProfileId id;
    private DisplayName displayName;
    private boolean active;
    private List<ProfileLocalization> localizations;
    private List<ExternalProfileLink> externalLinks;
    private final Instant createdAt;
    private Instant updatedAt;
    private Version version;

    private PersonalProfile(
            ProfileId id,
            DisplayName displayName,
            boolean active,
            List<ProfileLocalization> localizations,
            List<ExternalProfileLink> externalLinks,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.active = active;
        this.localizations = List.copyOf(Objects.requireNonNull(localizations, "localizations"));
        this.externalLinks = List.copyOf(Objects.requireNonNull(externalLinks, "externalLinks"));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = Objects.requireNonNull(version, "version");
        if (updatedAt.isBefore(createdAt)) {
            throw new PortfolioValidationException("updatedAt must not be before createdAt");
        }
        rejectDuplicate(localizations, ProfileLocalization::language, "profile localization language");
        rejectDuplicate(externalLinks, ExternalProfileLink::sortOrder, "external link sort order");
    }

    public static PersonalProfile create(
            ProfileId id,
            DisplayName displayName,
            boolean active,
            List<ProfileLocalization> localizations,
            List<ExternalProfileLink> externalLinks,
            Instant now) {
        return new PersonalProfile(
                id,
                displayName,
                active,
                localizations,
                externalLinks,
                now,
                now,
                Version.initial());
    }

    public static PersonalProfile rehydrate(
            ProfileId id,
            DisplayName displayName,
            boolean active,
            List<ProfileLocalization> localizations,
            List<ExternalProfileLink> externalLinks,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        return new PersonalProfile(
                id,
                displayName,
                active,
                localizations,
                externalLinks,
                createdAt,
                updatedAt,
                version);
    }

    public void activate(Instant now) {
        active = true;
        markUpdated(now);
    }

    public void deactivate(Instant now) {
        active = false;
        markUpdated(now);
    }

    public void replaceLocalizations(List<ProfileLocalization> localizations, Instant now) {
        this.localizations = List.copyOf(Objects.requireNonNull(localizations, "localizations"));
        rejectDuplicate(this.localizations, ProfileLocalization::language, "profile localization language");
        markUpdated(now);
    }

    public void replaceExternalLinks(List<ExternalProfileLink> externalLinks, Instant now) {
        this.externalLinks = List.copyOf(Objects.requireNonNull(externalLinks, "externalLinks"));
        rejectDuplicate(this.externalLinks, ExternalProfileLink::sortOrder, "external link sort order");
        markUpdated(now);
    }

    public void updateActiveProfile(
            DisplayName displayName,
            List<ProfileLocalization> localizations,
            List<ExternalProfileLink> externalLinks,
            Instant now) {
        Objects.requireNonNull(now, "now");
        List<ProfileLocalization> localizationsCopy =
                List.copyOf(Objects.requireNonNull(localizations, "localizations"));
        List<ExternalProfileLink> externalLinksCopy =
                List.copyOf(Objects.requireNonNull(externalLinks, "externalLinks"));
        rejectDuplicate(localizationsCopy, ProfileLocalization::language, "profile localization language");
        rejectDuplicate(externalLinksCopy, ExternalProfileLink::sortOrder, "external link sort order");
        if (now.isBefore(createdAt)) {
            throw new PortfolioValidationException("updatedAt must not be before createdAt");
        }
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.active = true;
        this.localizations = localizationsCopy;
        this.externalLinks = externalLinksCopy;
        this.updatedAt = now;
        this.version = version.next();
    }

    public boolean hasLocalization(ContentLanguage language) {
        Objects.requireNonNull(language, "language");
        return localizations.stream().anyMatch(localization -> localization.language() == language);
    }

    private void markUpdated(Instant now) {
        updatedAt = Objects.requireNonNull(now, "now");
        if (updatedAt.isBefore(createdAt)) {
            throw new PortfolioValidationException("updatedAt must not be before createdAt");
        }
        version = version.next();
    }

    private static <T, K> void rejectDuplicate(List<T> values, Function<T, K> key, String label) {
        Set<K> seen = new HashSet<>();
        for (T value : values) {
            if (!seen.add(key.apply(value))) {
                throw new PortfolioValidationException("duplicate " + label);
            }
        }
    }

    public ProfileId id() {
        return id;
    }

    public DisplayName displayName() {
        return displayName;
    }

    public boolean active() {
        return active;
    }

    public List<ProfileLocalization> localizations() {
        return localizations;
    }

    public List<ExternalProfileLink> externalLinks() {
        return externalLinks;
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
