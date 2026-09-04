package dev.persefonia.profileportfolio.application.publicview;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.project.Project;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class ProjectPublicMutationFactsFactory {
    private static final ProjectPublicExposureSnapshot NONE =
            new ProjectPublicExposureSnapshot(false, false, false, false);

    private final ProjectPublicExposurePolicy exposurePolicy;
    private final ProjectPublicRouteFactory routeFactory;

    public ProjectPublicMutationFactsFactory(
            ProjectPublicExposurePolicy exposurePolicy,
            ProjectPublicRouteFactory routeFactory) {
        this.exposurePolicy = Objects.requireNonNull(exposurePolicy, "exposurePolicy");
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
    }

    public State capture(Project project) {
        Map<ContentLanguage, PublicUrl> routes = new EnumMap<>(ContentLanguage.class);
        project.localizations().forEach(localization -> routes.put(
                localization.language(), routeFactory.publicUrl(localization.language(), localization.slug())));
        return new State(exposurePolicy.snapshot(project), routes);
    }

    public ProjectPublicMutationFacts created(Project project) {
        State after = capture(project);
        return new ProjectPublicMutationFacts(project.id().value(), NONE, after.exposure(), Map.of(), after.routes());
    }

    public ProjectPublicMutationFacts between(Project project, State before) {
        State after = capture(project);
        return new ProjectPublicMutationFacts(
                project.id().value(), before.exposure(), after.exposure(), before.routes(), after.routes());
    }

    public record State(ProjectPublicExposureSnapshot exposure, Map<ContentLanguage, PublicUrl> routes) {
        public State {
            Objects.requireNonNull(exposure, "exposure");
            routes = Map.copyOf(Objects.requireNonNull(routes, "routes"));
        }
    }
}
