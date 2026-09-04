package dev.persefonia.profileportfolio.application.discovery;

import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionInput;
import dev.persefonia.discovery.application.projection.RemoveDiscoverableResourceCommand;
import dev.persefonia.profileportfolio.domain.project.Project;
import dev.persefonia.profileportfolio.domain.project.ProjectLocalization;
import dev.persefonia.profileportfolio.domain.project.ProjectStatus;
import dev.persefonia.profileportfolio.domain.project.ProjectVisibility;
import dev.persefonia.profileportfolio.application.publicview.ProjectPublicExposurePolicy;
import java.util.List;
import java.util.Objects;

public final class ProjectDiscoveryProjectionFactory {
    private final ProjectPublicRouteFactory routeFactory;
    private final ConfiguredProjectCanonicalUrlFactory canonicalUrlFactory;
    private final ProjectPublicExposurePolicy exposurePolicy;

    public ProjectDiscoveryProjectionFactory(
            ProjectPublicRouteFactory routeFactory,
            ConfiguredProjectCanonicalUrlFactory canonicalUrlFactory) {
        this(routeFactory, canonicalUrlFactory, new ProjectPublicExposurePolicy());
    }

    public ProjectDiscoveryProjectionFactory(
            ProjectPublicRouteFactory routeFactory,
            ConfiguredProjectCanonicalUrlFactory canonicalUrlFactory,
            ProjectPublicExposurePolicy exposurePolicy) {
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory");
        this.exposurePolicy = Objects.requireNonNull(exposurePolicy, "exposurePolicy");
    }

    public List<DiscoverableResourceProjectionInput> projectionsFor(Project project) {
        Objects.requireNonNull(project, "project");
        if (!exposurePolicy.snapshot(project).directReachable()) {
            return List.of();
        }
        return project.localizations().stream()
                .map(localization -> projectionFor(project, localization))
                .toList();
    }

    public RemoveDiscoverableResourceCommand removeCommandFor(Project project) {
        Objects.requireNonNull(project, "project");
        return new RemoveDiscoverableResourceCommand(
                SourceContext.PROFILE_PORTFOLIO,
                SourceType.PROJECT,
                new SourceEntityId(project.id().value()));
    }

    private DiscoverableResourceProjectionInput projectionFor(Project project, ProjectLocalization localization) {
        var publicUrl = routeFactory.publicUrl(localization.language(), localization.slug());
        String title = localization.title().value();
        String summary = localization.summary().value();
        return new DiscoverableResourceProjectionInput(
                SourceContext.PROFILE_PORTFOLIO,
                SourceType.PROJECT,
                new SourceEntityId(project.id().value()),
                DiscoverableResourceType.PROJECT,
                RoutePurpose.DETAIL,
                DiscoveryLanguage.valueOf(localization.language().name()),
                publicUrl,
                canonicalUrlFactory.canonicalUrl(publicUrl),
                title,
                summary,
                indexingPolicy(project),
                searchEligibility(project),
                sitemapEligibility(project),
                DiscoveryEligibility.NOT_ELIGIBLE,
                title,
                summary,
                null,
                null,
                project.updatedAt(),
                title + "\n" + summary);
    }

    private IndexingPolicy indexingPolicy(Project project) {
        return exposurePolicy.snapshot(project).listed()
                ? IndexingPolicy.INDEX
                : IndexingPolicy.NO_INDEX;
    }

    private DiscoveryEligibility searchEligibility(Project project) {
        return exposurePolicy.snapshot(project).listed()
                ? DiscoveryEligibility.ELIGIBLE
                : DiscoveryEligibility.NOT_ELIGIBLE;
    }

    private DiscoveryEligibility sitemapEligibility(Project project) {
        return exposurePolicy.snapshot(project).sitemapEligible()
                ? DiscoveryEligibility.ELIGIBLE
                : DiscoveryEligibility.NOT_ELIGIBLE;
    }
}
