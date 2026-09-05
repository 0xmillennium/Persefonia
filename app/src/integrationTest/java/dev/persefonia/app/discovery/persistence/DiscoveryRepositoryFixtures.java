package dev.persefonia.app.discovery.persistence;

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
import dev.persefonia.discovery.domain.DiscoverableResource;
import dev.persefonia.discovery.domain.DiscoverableResourceId;
import dev.persefonia.discovery.domain.DiscoverableResourceKey;
import dev.persefonia.discovery.domain.OpenGraphDescription;
import dev.persefonia.discovery.domain.OpenGraphTitle;
import dev.persefonia.discovery.domain.RedirectRule;
import dev.persefonia.discovery.domain.RedirectRuleId;
import dev.persefonia.discovery.domain.ResourceSummary;
import dev.persefonia.discovery.domain.ResourceTitle;
import dev.persefonia.discovery.domain.SearchText;
import dev.persefonia.discovery.domain.SocialPreviewProfile;
import dev.persefonia.discovery.domain.SourceEntityRef;
import dev.persefonia.discovery.domain.Version;
import java.time.Instant;
import java.util.UUID;

final class DiscoveryRepositoryFixtures {
    static final Instant NOW = Instant.parse("2026-06-14T10:00:00Z");
    static final SourceEntityRef SOURCE_REF = new SourceEntityRef(
            SourceContext.CONTENT_PUBLISHING, SourceType.CONTENT_ITEM,
            new SourceEntityId(UUID.fromString("10000000-0000-0000-0000-000000000001")));

    private DiscoveryRepositoryFixtures() {
    }

    static DiscoverableResource resource(String suffix) {
        return resource(DiscoverableResourceId.random(), suffix, DiscoverableResourceType.ARTICLE);
    }

    static DiscoverableResource resource(
            DiscoverableResourceId id, String suffix, DiscoverableResourceType resourceType) {
        return DiscoverableResource.createCurrent(
                id,
                new DiscoverableResourceKey(
                        SOURCE_REF.sourceContext(), SOURCE_REF.sourceType(), SOURCE_REF.sourceEntityId(),
                        resourceType, DiscoveryLanguage.EN, RoutePurpose.DETAIL),
                new PublicUrl("/" + suffix),
                new CanonicalUrl("https://example.test/" + suffix),
                new ResourceTitle("Title " + suffix),
                new ResourceSummary("Summary " + suffix),
                IndexingPolicy.INDEX,
                DiscoveryEligibility.ELIGIBLE,
                DiscoveryEligibility.ELIGIBLE,
                DiscoveryEligibility.ELIGIBLE,
                new SocialPreviewProfile(
                        new OpenGraphTitle("OG " + suffix),
                        new OpenGraphDescription("OG description " + suffix),
                        UUID.fromString("20000000-0000-0000-0000-000000000001")),
                NOW.minusSeconds(60),
                NOW.minusSeconds(30),
                new SearchText("Search " + suffix),
                NOW,
                Version.initial());
    }

    static RedirectRule manualRedirect(String suffix, boolean active) {
        return RedirectRule.create(
                RedirectRuleId.random(),
                new PublicUrl("/old-" + suffix),
                new PublicUrl("/new-" + suffix),
                RedirectStatusCode.PERMANENT_REDIRECT_308,
                dev.persefonia.discovery.application.contract.RedirectReason.MANUAL,
                SOURCE_REF,
                active,
                NOW,
                NOW,
                Version.initial());
    }
}
