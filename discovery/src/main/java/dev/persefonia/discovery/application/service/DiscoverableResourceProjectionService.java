package dev.persefonia.discovery.application.service;

import dev.persefonia.discovery.application.port.RemoveDiscoverableResourcePort;
import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionInput;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.discovery.application.projection.RemoveDiscoverableResourceCommand;
import dev.persefonia.discovery.domain.DiscoverableResource;
import dev.persefonia.discovery.domain.DiscoverableResourceId;
import dev.persefonia.discovery.domain.DiscoverableResourceKey;
import dev.persefonia.discovery.domain.DiscoverableResourceRepository;
import dev.persefonia.discovery.domain.OpenGraphDescription;
import dev.persefonia.discovery.domain.OpenGraphTitle;
import dev.persefonia.discovery.domain.ResourceSummary;
import dev.persefonia.discovery.domain.ResourceTitle;
import dev.persefonia.discovery.domain.SearchText;
import dev.persefonia.discovery.domain.SocialPreviewProfile;
import dev.persefonia.discovery.domain.SourceEntityRef;
import dev.persefonia.discovery.domain.Version;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class DiscoverableResourceProjectionService
        implements UpdateDiscoverableResourcePort, RemoveDiscoverableResourcePort {
    private final DiscoverableResourceRepository repository;
    private final Clock clock;

    public DiscoverableResourceProjectionService(DiscoverableResourceRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public DiscoverableResourceProjectionResult update(DiscoverableResourceProjectionInput input) {
        requireArgument(input, "input");

        DiscoverableResourceKey key = new DiscoverableResourceKey(
                input.sourceContext(),
                input.sourceType(),
                input.sourceEntityId(),
                input.resourceType(),
                input.language(),
                input.routePurpose());
        Instant createdAt = Instant.now(clock);
        DiscoverableResource resource = DiscoverableResource.createCurrent(
                DiscoverableResourceId.random(),
                key,
                input.publicUrl(),
                input.canonicalUrl(),
                new ResourceTitle(input.title()),
                new ResourceSummary(input.summary()),
                input.indexingPolicy(),
                input.searchEligibility(),
                input.sitemapEligibility(),
                input.feedEligibility(),
                socialPreview(input),
                input.publishedAt(),
                input.sourceUpdatedAt(),
                new SearchText(input.searchText()),
                createdAt,
                Version.initial());

        repository.replaceByKey(resource);
        return new DiscoverableResourceProjectionResult.Updated();
    }

    @Override
    public DiscoverableResourceProjectionResult remove(RemoveDiscoverableResourceCommand command) {
        requireArgument(command, "command");
        SourceEntityRef sourceRef =
                new SourceEntityRef(command.sourceContext(), command.sourceType(), command.sourceEntityId());
        return repository.removeBySourceRef(sourceRef) > 0
                ? new DiscoverableResourceProjectionResult.Removed()
                : new DiscoverableResourceProjectionResult.Noop();
    }

    private static SocialPreviewProfile socialPreview(DiscoverableResourceProjectionInput input) {
        if (input.openGraphTitle() == null
                && input.openGraphDescription() == null
                && input.openGraphImageAssetId() == null) {
            return SocialPreviewProfile.empty();
        }
        return new SocialPreviewProfile(
                input.openGraphTitle() == null ? null : new OpenGraphTitle(input.openGraphTitle()),
                input.openGraphDescription() == null ? null : new OpenGraphDescription(input.openGraphDescription()),
                input.openGraphImageAssetId());
    }

    private static void requireArgument(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }
}
