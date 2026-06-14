package dev.persefonia.discovery.application.service;

import dev.persefonia.discovery.application.port.ResolvePublicRoutePort;
import dev.persefonia.discovery.application.route.PublicRouteLookup;
import dev.persefonia.discovery.application.route.PublicRouteResolution;
import dev.persefonia.discovery.domain.DiscoverableResource;
import dev.persefonia.discovery.domain.DiscoverableResourceRepository;
import dev.persefonia.discovery.domain.RedirectRule;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
import java.util.Objects;

public final class PublicRouteResolutionService implements ResolvePublicRoutePort {
    private final RedirectRuleRepository redirectRules;
    private final DiscoverableResourceRepository resources;

    public PublicRouteResolutionService(
            RedirectRuleRepository redirectRules,
            DiscoverableResourceRepository resources) {
        this.redirectRules = Objects.requireNonNull(redirectRules, "redirectRules");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @Override
    public PublicRouteResolution resolve(PublicRouteLookup lookup) {
        if (lookup == null) {
            throw new IllegalArgumentException("lookup must not be null");
        }

        return redirectRules.findActiveBySourceUrl(lookup.publicUrl())
                .<PublicRouteResolution>map(PublicRouteResolutionService::redirect)
                .orElseGet(() -> resources.findByPublicUrl(lookup.publicUrl())
                        .<PublicRouteResolution>map(PublicRouteResolutionService::found)
                        .orElseGet(PublicRouteResolution.NotFound::new));
    }

    private static PublicRouteResolution redirect(RedirectRule rule) {
        return new PublicRouteResolution.Redirect(rule.statusCode(), rule.targetUrl());
    }

    private static PublicRouteResolution found(DiscoverableResource resource) {
        return new PublicRouteResolution.Found(
                resource.sourceRef().sourceContext(),
                resource.sourceRef().sourceType(),
                resource.sourceRef().sourceEntityId(),
                resource.resourceType(),
                resource.routePurpose(),
                resource.language(),
                resource.publicUrl(),
                resource.canonicalUrl(),
                resource.indexingPolicy());
    }
}
