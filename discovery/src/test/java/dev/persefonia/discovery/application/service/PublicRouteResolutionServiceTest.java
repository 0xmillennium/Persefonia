package dev.persefonia.discovery.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.contract.CanonicalUrl;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.route.PublicRouteLookup;
import dev.persefonia.discovery.application.route.PublicRouteResolution;
import dev.persefonia.discovery.domain.DiscoverableResource;
import dev.persefonia.discovery.domain.DiscoverableResourceId;
import dev.persefonia.discovery.domain.DiscoverableResourceKey;
import dev.persefonia.discovery.domain.DiscoverableResourceRepository;
import dev.persefonia.discovery.domain.RedirectRule;
import dev.persefonia.discovery.domain.RedirectRuleId;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
import dev.persefonia.discovery.domain.ResourceSummary;
import dev.persefonia.discovery.domain.ResourceTitle;
import dev.persefonia.discovery.domain.SearchText;
import dev.persefonia.discovery.domain.SocialPreviewProfile;
import dev.persefonia.discovery.domain.SourceEntityRef;
import dev.persefonia.discovery.domain.Version;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicRouteResolutionServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-14T08:00:00Z");
    private static final PublicUrl REQUESTED_URL = new PublicUrl("/articles/current");

    @Test
    void redirectTakesPrecedenceOverResourceAndMapsOnlyRouteResult() {
        InMemoryRedirectRuleRepository redirects = new InMemoryRedirectRuleRepository();
        redirects.rule = Optional.of(redirect(REQUESTED_URL, new PublicUrl("/articles/new")));
        InMemoryDiscoverableResourceRepository resources = new InMemoryDiscoverableResourceRepository();
        resources.resource = Optional.of(resource());

        PublicRouteResolution result =
                new PublicRouteResolutionService(redirects, resources).resolve(new PublicRouteLookup(REQUESTED_URL));

        assertThat(result).isEqualTo(new PublicRouteResolution.Redirect(
                RedirectStatusCode.MOVED_PERMANENTLY_301, new PublicUrl("/articles/new")));
        assertThat(resources.lookupCount).isZero();
    }

    @Test
    void returnsSafeFoundMetadataWhenNoRedirectExists() {
        InMemoryRedirectRuleRepository redirects = new InMemoryRedirectRuleRepository();
        InMemoryDiscoverableResourceRepository resources = new InMemoryDiscoverableResourceRepository();
        resources.resource = Optional.of(resource());

        PublicRouteResolution result =
                new PublicRouteResolutionService(redirects, resources).resolve(new PublicRouteLookup(REQUESTED_URL));

        assertThat(result).isEqualTo(new PublicRouteResolution.Found(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                sourceId(),
                DiscoverableResourceType.ARTICLE,
                RoutePurpose.DETAIL,
                DiscoveryLanguage.EN,
                REQUESTED_URL,
                new CanonicalUrl("https://example.test/articles/current"),
                IndexingPolicy.INDEX));
        assertThat(PublicRouteResolution.Found.class.getRecordComponents())
                .noneMatch(component -> component.getType().equals(DiscoverableResource.class));
    }

    @Test
    void returnsReasonlessNotFoundWhenNothingMatches() {
        PublicRouteResolution result = new PublicRouteResolutionService(
                        new InMemoryRedirectRuleRepository(), new InMemoryDiscoverableResourceRepository())
                .resolve(new PublicRouteLookup(REQUESTED_URL));

        assertThat(result).isEqualTo(new PublicRouteResolution.NotFound());
        assertThat(PublicRouteResolution.NotFound.class.getRecordComponents()).isEmpty();
    }

    @Test
    void rejectsNullLookup() {
        PublicRouteResolutionService service =
                new PublicRouteResolutionService(new InMemoryRedirectRuleRepository(), new InMemoryDiscoverableResourceRepository());

        assertThatThrownBy(() -> service.resolve(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static DiscoverableResource resource() {
        DiscoverableResourceKey key = new DiscoverableResourceKey(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                sourceId(),
                DiscoverableResourceType.ARTICLE,
                DiscoveryLanguage.EN,
                RoutePurpose.DETAIL);
        return DiscoverableResource.createCurrent(
                DiscoverableResourceId.random(),
                key,
                REQUESTED_URL,
                new CanonicalUrl("https://example.test/articles/current"),
                new ResourceTitle("Title"),
                new ResourceSummary("Summary"),
                IndexingPolicy.INDEX,
                DiscoveryEligibility.ELIGIBLE,
                DiscoveryEligibility.ELIGIBLE,
                DiscoveryEligibility.ELIGIBLE,
                SocialPreviewProfile.empty(),
                NOW,
                NOW,
                new SearchText("Search text"),
                NOW,
                Version.initial());
    }

    private static RedirectRule redirect(PublicUrl sourceUrl, PublicUrl targetUrl) {
        return RedirectRule.createManual(
                RedirectRuleId.random(),
                sourceUrl,
                targetUrl,
                RedirectStatusCode.MOVED_PERMANENTLY_301,
                null,
                NOW,
                Version.initial());
    }

    private static SourceEntityId sourceId() {
        return new SourceEntityId(UUID.fromString("5b91a38c-bddc-439b-b89a-5c42231b62ad"));
    }

    private static final class InMemoryRedirectRuleRepository implements RedirectRuleRepository {
        private Optional<RedirectRule> rule = Optional.empty();

        @Override
        public RedirectRule save(RedirectRule redirectRule) {
            return redirectRule;
        }

        @Override
        public Optional<RedirectRule> findById(RedirectRuleId id) {
            return Optional.empty();
        }

        @Override
        public Optional<RedirectRule> findActiveBySourceUrl(PublicUrl sourceUrl) {
            return rule.filter(candidate -> candidate.sourceUrl().equals(sourceUrl));
        }

        @Override
        public List<RedirectRule> findBySourceRef(SourceEntityRef sourceRef) {
            return List.of();
        }

        @Override
        public Optional<RedirectRule> deactivate(RedirectRuleId id, Instant updatedAt) {
            return Optional.empty();
        }
    }

    private static final class InMemoryDiscoverableResourceRepository implements DiscoverableResourceRepository {
        private Optional<DiscoverableResource> resource = Optional.empty();
        private int lookupCount;

        @Override
        public DiscoverableResource save(DiscoverableResource value) {
            return value;
        }

        @Override
        public DiscoverableResource replaceByKey(DiscoverableResource value) {
            return value;
        }

        @Override
        public Optional<DiscoverableResource> findById(DiscoverableResourceId id) {
            return Optional.empty();
        }

        @Override
        public Optional<DiscoverableResource> findByKey(DiscoverableResourceKey key) {
            return Optional.empty();
        }

        @Override
        public Optional<DiscoverableResource> findByPublicUrl(PublicUrl publicUrl) {
            lookupCount++;
            return resource.filter(candidate -> candidate.publicUrl().equals(publicUrl));
        }

        @Override
        public List<DiscoverableResource> findBySourceRef(SourceEntityRef sourceRef) {
            return List.of();
        }

        @Override
        public int removeBySourceRef(SourceEntityRef sourceRef) {
            return 0;
        }
    }
}
