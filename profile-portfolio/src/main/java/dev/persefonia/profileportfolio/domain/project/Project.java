package dev.persefonia.profileportfolio.domain.project;

import dev.persefonia.profileportfolio.domain.common.AssetId;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.common.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class Project {
    private final ProjectId id;
    private ProjectStatus status;
    private ProjectVisibility visibility;
    private boolean featured;
    private SortOrder sortOrder;
    private AssetId coverAssetId;
    private Set<TagId> tagIds;
    private List<ProjectTechnology> technologies;
    private List<ProjectLink> links;
    private List<ProjectLocalization> localizations;
    private final Instant createdAt;
    private Instant updatedAt;
    private Version version;

    private Project(
            ProjectId id,
            ProjectStatus status,
            ProjectVisibility visibility,
            boolean featured,
            SortOrder sortOrder,
            AssetId coverAssetId,
            Set<TagId> tagIds,
            List<ProjectTechnology> technologies,
            List<ProjectLink> links,
            List<ProjectLocalization> localizations,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        this.id = Objects.requireNonNull(id, "id");
        this.status = Objects.requireNonNull(status, "status");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.featured = featured;
        this.sortOrder = sortOrder;
        this.coverAssetId = coverAssetId;
        this.tagIds = Set.copyOf(Objects.requireNonNull(tagIds, "tagIds"));
        this.technologies = List.copyOf(Objects.requireNonNull(technologies, "technologies"));
        this.links = List.copyOf(Objects.requireNonNull(links, "links"));
        this.localizations = List.copyOf(Objects.requireNonNull(localizations, "localizations"));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = Objects.requireNonNull(version, "version");
        validateStructuralInvariants();
    }

    public static Project create(
            ProjectId id,
            ProjectStatus status,
            ProjectVisibility visibility,
            boolean featured,
            SortOrder sortOrder,
            AssetId coverAssetId,
            Set<TagId> tagIds,
            List<ProjectTechnology> technologies,
            List<ProjectLink> links,
            List<ProjectLocalization> localizations,
            ContentLanguage defaultLanguage,
            Instant now) {
        Project project = new Project(
                id,
                status,
                visibility,
                featured,
                sortOrder,
                coverAssetId,
                tagIds,
                technologies,
                links,
                localizations,
                now,
                now,
                Version.initial());
        project.validateFeaturedEligibility(defaultLanguage);
        return project;
    }

    public static Project rehydrate(
            ProjectId id,
            ProjectStatus status,
            ProjectVisibility visibility,
            boolean featured,
            SortOrder sortOrder,
            AssetId coverAssetId,
            Set<TagId> tagIds,
            List<ProjectTechnology> technologies,
            List<ProjectLink> links,
            List<ProjectLocalization> localizations,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        return new Project(
                id,
                status,
                visibility,
                featured,
                sortOrder,
                coverAssetId,
                tagIds,
                technologies,
                links,
                localizations,
                createdAt,
                updatedAt,
                version);
    }

    public void setFeatured(boolean featured, ContentLanguage defaultLanguage, Instant now) {
        this.featured = featured;
        validateStructuralInvariants();
        validateFeaturedEligibility(defaultLanguage);
        markUpdated(now);
    }

    public void changeStatus(ProjectStatus status, ContentLanguage defaultLanguage, Instant now) {
        this.status = Objects.requireNonNull(status, "status");
        validateStructuralInvariants();
        validateFeaturedEligibility(defaultLanguage);
        markUpdated(now);
    }

    public void changeVisibility(ProjectVisibility visibility, ContentLanguage defaultLanguage, Instant now) {
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        validateStructuralInvariants();
        validateFeaturedEligibility(defaultLanguage);
        markUpdated(now);
    }

    public void replaceLocalizations(List<ProjectLocalization> localizations, ContentLanguage defaultLanguage, Instant now) {
        this.localizations = List.copyOf(Objects.requireNonNull(localizations, "localizations"));
        validateStructuralInvariants();
        validateFeaturedEligibility(defaultLanguage);
        markUpdated(now);
    }

    public void replaceTechnologies(List<ProjectTechnology> technologies, ContentLanguage defaultLanguage, Instant now) {
        this.technologies = List.copyOf(Objects.requireNonNull(technologies, "technologies"));
        validateStructuralInvariants();
        validateFeaturedEligibility(defaultLanguage);
        markUpdated(now);
    }

    public void replaceLinks(List<ProjectLink> links, ContentLanguage defaultLanguage, Instant now) {
        this.links = List.copyOf(Objects.requireNonNull(links, "links"));
        validateStructuralInvariants();
        validateFeaturedEligibility(defaultLanguage);
        markUpdated(now);
    }

    public void replaceTags(Set<TagId> tagIds, ContentLanguage defaultLanguage, Instant now) {
        this.tagIds = Set.copyOf(Objects.requireNonNull(tagIds, "tagIds"));
        validateStructuralInvariants();
        validateFeaturedEligibility(defaultLanguage);
        markUpdated(now);
    }

    public void validateFeaturedEligibility(ContentLanguage defaultLanguage) {
        Objects.requireNonNull(defaultLanguage, "defaultLanguage");
        if (featured && localizations.stream().noneMatch(localization -> localization.language() == defaultLanguage)) {
            throw new ProjectValidationException("featured project must have default-language localization");
        }
    }

    private void markUpdated(Instant now) {
        updatedAt = Objects.requireNonNull(now, "now");
        if (updatedAt.isBefore(createdAt)) {
            throw new ProjectValidationException("updatedAt must not be before createdAt");
        }
        version = version.next();
    }

    private void validateStructuralInvariants() {
        if (updatedAt.isBefore(createdAt)) {
            throw new ProjectValidationException("updatedAt must not be before createdAt");
        }
        if (visibility == ProjectVisibility.PUBLIC && localizations.isEmpty()) {
            throw new ProjectValidationException("public project must have localization");
        }
        if (featured && visibility != ProjectVisibility.PUBLIC) {
            throw new ProjectValidationException("featured project must be public");
        }
        if (featured && status == ProjectStatus.ARCHIVED) {
            throw new ProjectValidationException("featured project must not be archived");
        }
        rejectDuplicate(localizations, ProjectLocalization::language, "project localization language");
        rejectDuplicate(technologies, technology -> technology.normalizedName().value() + "\n" + technology.category(), "technology normalized name/category");
        rejectDuplicate(technologies, ProjectTechnology::sortOrder, "technology sort order");
        rejectDuplicate(links, ProjectLink::sortOrder, "project link sort order");
    }

    private static <T, K> void rejectDuplicate(List<T> values, Function<T, K> key, String label) {
        Set<K> seen = new HashSet<>();
        for (T value : values) {
            if (!seen.add(key.apply(value))) {
                throw new ProjectValidationException("duplicate " + label);
            }
        }
    }

    public ProjectId id() {
        return id;
    }

    public ProjectStatus status() {
        return status;
    }

    public ProjectVisibility visibility() {
        return visibility;
    }

    public boolean featured() {
        return featured;
    }

    public Optional<SortOrder> sortOrder() {
        return Optional.ofNullable(sortOrder);
    }

    public Optional<AssetId> coverAssetId() {
        return Optional.ofNullable(coverAssetId);
    }

    public Set<TagId> tagIds() {
        return tagIds;
    }

    public List<ProjectTechnology> technologies() {
        return technologies;
    }

    public List<ProjectLink> links() {
        return links;
    }

    public List<ProjectLocalization> localizations() {
        return localizations;
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
