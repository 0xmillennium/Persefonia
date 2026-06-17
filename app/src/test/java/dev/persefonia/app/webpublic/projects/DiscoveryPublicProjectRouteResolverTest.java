package dev.persefonia.app.webpublic.projects;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.discovery.application.contract.CanonicalUrl;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.port.ResolvePublicRoutePort;
import dev.persefonia.discovery.application.route.PublicRouteLookup;
import dev.persefonia.discovery.application.route.PublicRouteResolution;
import dev.persefonia.webpublic.projects.DiscoveryPublicProjectRouteOutcome;
import dev.persefonia.webpublic.projects.DiscoveryPublicProjectRouteResolver;
import dev.persefonia.webpublic.projects.PublicProjectRoute;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoveryPublicProjectRouteResolverTest {
    private final Resolver resolverPort = new Resolver();
    private final DiscoveryPublicProjectRouteResolver resolver = new DiscoveryPublicProjectRouteResolver(resolverPort);

    @Test
    void resolvesProjectProjection() {
        UUID projectId = UUID.randomUUID();
        resolverPort.resolution = found(projectId, SourceContext.PROFILE_PORTFOLIO, SourceType.PROJECT,
                DiscoverableResourceType.PROJECT, RoutePurpose.DETAIL, DiscoveryLanguage.EN, "/en/projects/demo");

        var outcome = resolver.resolve(new PublicProjectRoute(DiscoveryLanguage.EN, "demo"));

        assertThat(outcome).isInstanceOfSatisfying(DiscoveryPublicProjectRouteOutcome.Project.class, project -> {
            assertThat(project.projectId()).isEqualTo(projectId);
            assertThat(project.publicUrl()).isEqualTo("/en/projects/demo");
            assertThat(project.canonicalUrl()).isEqualTo("https://example.test/en/projects/demo");
        });
    }

    @Test
    void rejectsWrongProjectionMetadata() {
        resolverPort.resolution = found(UUID.randomUUID(), SourceContext.CONTENT_PUBLISHING, SourceType.PROJECT,
                DiscoverableResourceType.PROJECT, RoutePurpose.DETAIL, DiscoveryLanguage.EN, "/en/projects/demo");
        assertThat(resolver.resolve(new PublicProjectRoute(DiscoveryLanguage.EN, "demo")))
                .isInstanceOf(DiscoveryPublicProjectRouteOutcome.NotFound.class);

        resolverPort.resolution = found(UUID.randomUUID(), SourceContext.PROFILE_PORTFOLIO, SourceType.CONTENT_ITEM,
                DiscoverableResourceType.PROJECT, RoutePurpose.DETAIL, DiscoveryLanguage.EN, "/en/projects/demo");
        assertThat(resolver.resolve(new PublicProjectRoute(DiscoveryLanguage.EN, "demo")))
                .isInstanceOf(DiscoveryPublicProjectRouteOutcome.NotFound.class);

        resolverPort.resolution = found(UUID.randomUUID(), SourceContext.PROFILE_PORTFOLIO, SourceType.PROJECT,
                DiscoverableResourceType.TAG, RoutePurpose.DETAIL, DiscoveryLanguage.EN, "/en/projects/demo");
        assertThat(resolver.resolve(new PublicProjectRoute(DiscoveryLanguage.EN, "demo")))
                .isInstanceOf(DiscoveryPublicProjectRouteOutcome.NotFound.class);

        resolverPort.resolution = found(UUID.randomUUID(), SourceContext.PROFILE_PORTFOLIO, SourceType.PROJECT,
                DiscoverableResourceType.PROJECT, RoutePurpose.DETAIL, DiscoveryLanguage.TR, "/en/projects/demo");
        assertThat(resolver.resolve(new PublicProjectRoute(DiscoveryLanguage.EN, "demo")))
                .isInstanceOf(DiscoveryPublicProjectRouteOutcome.NotFound.class);

        resolverPort.resolution = found(UUID.randomUUID(), SourceContext.PROFILE_PORTFOLIO, SourceType.PROJECT,
                DiscoverableResourceType.PROJECT, RoutePurpose.DETAIL, DiscoveryLanguage.EN, "/en/projects/other");
        assertThat(resolver.resolve(new PublicProjectRoute(DiscoveryLanguage.EN, "demo")))
                .isInstanceOf(DiscoveryPublicProjectRouteOutcome.NotFound.class);
    }

    @Test
    void preservesRedirectOutcome() {
        resolverPort.resolution = new PublicRouteResolution.Redirect(
                RedirectStatusCode.MOVED_PERMANENTLY_301,
                new PublicUrl("/en/projects/new-demo"));

        assertThat(resolver.resolve(new PublicProjectRoute(DiscoveryLanguage.EN, "old-demo")))
                .isInstanceOfSatisfying(DiscoveryPublicProjectRouteOutcome.Redirect.class, redirect -> {
                    assertThat(redirect.statusCode()).isEqualTo(301);
                    assertThat(redirect.targetPath()).isEqualTo("/en/projects/new-demo");
                });
    }

    private static PublicRouteResolution.Found found(
            UUID sourceEntityId,
            SourceContext sourceContext,
            SourceType sourceType,
            DiscoverableResourceType resourceType,
            RoutePurpose routePurpose,
            DiscoveryLanguage language,
            String publicUrl) {
        return new PublicRouteResolution.Found(
                sourceContext,
                sourceType,
                new SourceEntityId(sourceEntityId),
                resourceType,
                routePurpose,
                language,
                new PublicUrl(publicUrl),
                new CanonicalUrl("https://example.test" + publicUrl),
                IndexingPolicy.NO_INDEX);
    }

    private static final class Resolver implements ResolvePublicRoutePort {
        private PublicRouteResolution resolution = new PublicRouteResolution.NotFound();

        @Override
        public PublicRouteResolution resolve(PublicRouteLookup lookup) {
            return resolution;
        }
    }
}
