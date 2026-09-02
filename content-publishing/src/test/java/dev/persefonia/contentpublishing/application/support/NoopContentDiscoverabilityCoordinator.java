package dev.persefonia.contentpublishing.application.support;

import dev.persefonia.contentpublishing.application.discovery.ConfiguredContentCanonicalUrlFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentDiscoverabilityCoordinator;
import dev.persefonia.contentpublishing.application.discovery.ContentDiscoveryProjectionFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentDiscoveryRedirectFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleChangeSummary;
import dev.persefonia.discovery.domain.RedirectRuleId;

public final class NoopContentDiscoverabilityCoordinator {
    private NoopContentDiscoverabilityCoordinator() {
    }

    public static ContentDiscoverabilityCoordinator create() {
        ContentPublicRouteFactory routeFactory = new ContentPublicRouteFactory();
        return new ContentDiscoverabilityCoordinator(
                input -> new DiscoverableResourceProjectionResult.Updated(),
                command -> new DiscoverableResourceProjectionResult.Noop(),
                command -> new RedirectRuleCreationResult.Noop(new RedirectRuleChangeSummary(
                        RedirectRuleId.random(),
                        command.sourceUrl(),
                        command.targetUrl(),
                        command.statusCode(),
                        command.reason())),
                new ContentDiscoveryProjectionFactory(
                        routeFactory,
                        new ConfiguredContentCanonicalUrlFactory("https://example.test")),
                new ContentDiscoveryRedirectFactory(routeFactory));
    }
}
