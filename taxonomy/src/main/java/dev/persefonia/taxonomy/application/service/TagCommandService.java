package dev.persefonia.taxonomy.application.service;

import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandAuthorizationPolicy;
import dev.persefonia.taxonomy.application.command.ArchiveTagCommand;
import dev.persefonia.taxonomy.application.command.CreateTagCommand;
import dev.persefonia.taxonomy.application.command.TagCommandResult;
import dev.persefonia.taxonomy.application.command.UpdateTagCommand;
import dev.persefonia.taxonomy.application.exception.TagCommandRejectedException;
import dev.persefonia.taxonomy.application.exception.TagCommandRejectedException.Reason;
import dev.persefonia.taxonomy.application.exception.TagNotFoundException;
import dev.persefonia.taxonomy.application.discovery.TagDiscoverabilityCoordinator;
import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagDescription;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagName;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import dev.persefonia.taxonomy.domain.service.TagNormalizationService;
import java.util.Objects;

public final class TagCommandService {
    private final TagRepository tags;
    private final TagNormalizationService normalization;
    private final TaxonomyCommandAuthorizationPolicy authorization;
    private final TagDiscoverabilityCoordinator discoverability;

    public TagCommandService(
            TagRepository tags,
            TagNormalizationService normalization,
            TaxonomyCommandAuthorizationPolicy authorization,
            TagDiscoverabilityCoordinator discoverability) {
        this.tags = Objects.requireNonNull(tags, "tags");
        this.normalization = Objects.requireNonNull(normalization, "normalization");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.discoverability = Objects.requireNonNull(discoverability, "discoverability");
    }

    public TagCommandResult create(CreateTagCommand command) {
        authorization.requireOwner(command.actor(), "taxonomy.tag.create");
        TagName name = TagName.of(command.name());
        NormalizedTagName normalizedName = normalization.normalizeName(name);
        TagSlug slug = slug(command.slug(), name);
        requireUnique(null, slug, normalizedName);
        Tag saved = tags.save(Tag.create(
                TagId.newId(), name, normalizedName, slug, TagDescription.ofNullable(command.description()),
                command.requestedAt()));
        discoverability.sync(saved);
        return result(saved, null, true);
    }

    public TagCommandResult update(UpdateTagCommand command) {
        authorization.requireOwner(command.actor(), "taxonomy.tag.update");
        Tag tag = requiredTag(command.tagId());
        TagSlug oldSlug = tag.slug();
        TagName name = TagName.of(command.name());
        NormalizedTagName normalizedName = normalization.normalizeName(name);
        TagSlug slug = slug(command.slug(), name);
        requireUnique(tag.id(), slug, normalizedName);
        tag.update(name, normalizedName, slug, TagDescription.ofNullable(command.description()), command.requestedAt());
        Tag saved = tags.save(tag);
        discoverability.sync(saved);
        return result(saved, oldSlug, true);
    }

    public TagCommandResult archive(ArchiveTagCommand command) {
        authorization.requireOwner(command.actor(), "taxonomy.tag.archive");
        Tag tag = requiredTag(command.tagId());
        if (tag.isArchived()) {
            discoverability.sync(tag);
            return result(tag, tag.slug(), false);
        }
        tag.archive(command.requestedAt());
        tag = tags.save(tag);
        discoverability.sync(tag);
        return result(tag, tag.slug(), true);
    }

    private Tag requiredTag(TagId id) {
        return tags.findById(id).orElseThrow(() -> new TagNotFoundException(id));
    }

    private TagSlug slug(String explicitSlug, TagName name) {
        return explicitSlug == null || explicitSlug.isBlank()
                ? normalization.generateSlug(name)
                : normalization.normalizeSlug(explicitSlug);
    }

    private void requireUnique(TagId currentId, TagSlug slug, NormalizedTagName normalizedName) {
        tags.findBySlug(slug)
                .filter(existing -> !existing.id().equals(currentId))
                .ifPresent(existing -> {
                    throw new TagCommandRejectedException(Reason.DUPLICATE_SLUG, "Tag slug already exists");
                });
        tags.findByNormalizedName(normalizedName)
                .filter(existing -> !existing.id().equals(currentId))
                .ifPresent(existing -> {
                    throw new TagCommandRejectedException(
                            Reason.DUPLICATE_NORMALIZED_NAME, "Normalized tag name already exists");
                });
    }

    private static TagCommandResult result(Tag tag, TagSlug oldSlug, boolean mutated) {
        return new TagCommandResult(tag.id(), tag.status(), tag.updatedAt(), mutated,
                java.util.Optional.ofNullable(oldSlug), tag.slug());
    }
}
