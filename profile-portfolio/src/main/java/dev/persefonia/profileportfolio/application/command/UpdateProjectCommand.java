package dev.persefonia.profileportfolio.application.command;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UpdateProjectCommand(
        PortfolioCommandActor actor,
        UUID projectId,
        String status,
        String visibility,
        boolean featured,
        Integer sortOrder,
        Set<UUID> tagIds,
        List<ProjectLocalizationInput> localizations,
        List<ProjectTechnologyInput> technologies,
        List<ProjectLinkInput> links,
        Instant requestedAt,
        String coverAssetId) {
    public UpdateProjectCommand(PortfolioCommandActor actor, UUID projectId, String status, String visibility,
            boolean featured, Integer sortOrder, Set<UUID> tagIds, List<ProjectLocalizationInput> localizations,
            List<ProjectTechnologyInput> technologies, List<ProjectLinkInput> links, Instant requestedAt) {
        this(actor, projectId, status, visibility, featured, sortOrder, tagIds, localizations, technologies, links, requestedAt, null);
    }
    public UpdateProjectCommand {
        tagIds = Set.copyOf(tagIds == null ? Set.of() : tagIds);
        localizations = List.copyOf(localizations == null ? List.of() : localizations);
        technologies = List.copyOf(technologies == null ? List.of() : technologies);
        links = List.copyOf(links == null ? List.of() : links);
    }
}
