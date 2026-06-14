package dev.persefonia.discovery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.contract.CanonicalUrl;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class DiscoverableResourceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-14T08:00:00Z");

    @Test
    void createsListedArticleAndDerivesKeyAttributes() {
        DiscoverableResource resource = listedResource(DiscoverableResourceType.ARTICLE);

        assertThat(resource.sourceRef()).isEqualTo(resource.key().sourceRef());
        assertThat(resource.resourceType()).isEqualTo(DiscoverableResourceType.ARTICLE);
        assertThat(resource.routePurpose()).isEqualTo(RoutePurpose.DETAIL);
        assertThat(resource.language()).isEqualTo(DiscoveryLanguage.EN);
        assertThat(resource.indexingPolicy()).isEqualTo(IndexingPolicy.INDEX);
        assertThat(resource.feedEligibility()).isEqualTo(DiscoveryEligibility.ELIGIBLE);
    }

    @Test
    void createsListedPageWithoutFeedEligibilityAndUnlistedResource() {
        DiscoverableResource page = listedResource(DiscoverableResourceType.PAGE);
        DiscoverableResource unlisted = resource(
                values -> values.withEligibility(DiscoverableResourceEligibilityPolicy.unlisted()));

        assertThat(page.feedEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
        assertThat(unlisted.indexingPolicy()).isEqualTo(IndexingPolicy.NO_INDEX);
        assertThat(unlisted.searchEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
        assertThat(unlisted.sitemapEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
        assertThat(unlisted.feedEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
    }

    @Test
    void requiresAggregateAndProjectionFields() {
        assertThatThrownBy(() -> resource(values -> values.withId(null))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resource(values -> values.withKey(null))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resource(values -> values.withPublicUrl(null))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resource(values -> values.withCanonicalUrl(null))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resource(values -> values.withTitle(null))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resource(values -> values.withSummary(null))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resource(values -> values.withSearchText(null))).isInstanceOf(NullPointerException.class);
    }

    @Test
    void replacementUpdatesProjectionAndPreservesCurrentIdentity() {
        DiscoverableResource original = listedResource(DiscoverableResourceType.ARTICLE);
        DiscoverableResourceEligibility unlisted = DiscoverableResourceEligibilityPolicy.unlisted();
        SocialPreviewProfile openGraph = new SocialPreviewProfile(
                new OpenGraphTitle("Replacement OG title"),
                new OpenGraphDescription("Replacement OG description"),
                UUID.fromString("ef952851-c855-4428-8476-fb97c5971993"));

        DiscoverableResource replaced = original.replaceCurrentProjection(
                new PublicUrl("/en/articles/replacement"),
                new CanonicalUrl("https://persefonia.dev/en/articles/replacement"),
                new ResourceTitle("Replacement"),
                new ResourceSummary("Replacement summary"),
                unlisted.indexingPolicy(),
                unlisted.searchEligibility(),
                unlisted.sitemapEligibility(),
                unlisted.feedEligibility(),
                openGraph,
                CREATED_AT.plusSeconds(10),
                CREATED_AT.plusSeconds(20),
                new SearchText("Replacement search text"));

        assertThat(replaced)
                .extracting(
                        DiscoverableResource::publicUrl,
                        DiscoverableResource::canonicalUrl,
                        DiscoverableResource::title,
                        DiscoverableResource::summary,
                        DiscoverableResource::indexingPolicy,
                        DiscoverableResource::openGraph,
                        DiscoverableResource::searchText)
                .containsExactly(
                        new PublicUrl("/en/articles/replacement"),
                        new CanonicalUrl("https://persefonia.dev/en/articles/replacement"),
                        new ResourceTitle("Replacement"),
                        new ResourceSummary("Replacement summary"),
                        IndexingPolicy.NO_INDEX,
                        openGraph,
                        new SearchText("Replacement search text"));
        assertThat(replaced.id()).isEqualTo(original.id());
        assertThat(replaced.key()).isEqualTo(original.key());
        assertThat(replaced.sourceRef()).isEqualTo(original.sourceRef());
        assertThat(replaced.createdAt()).isEqualTo(original.createdAt());
        assertThat(replaced.version()).isEqualTo(original.version().next());
        assertThat(original.publicUrl()).isEqualTo(new PublicUrl("/en/articles/baseline"));
    }

    @Test
    void fullKeyEqualityAllowsSameSourceReferenceToProduceDistinctResources() {
        DiscoverableResourceKey articleEn = key(DiscoverableResourceType.ARTICLE, DiscoveryLanguage.EN);
        DiscoverableResourceKey pageEn = key(DiscoverableResourceType.PAGE, DiscoveryLanguage.EN);
        DiscoverableResourceKey articleTr = key(DiscoverableResourceType.ARTICLE, DiscoveryLanguage.TR);

        assertThat(articleEn.sourceRef()).isEqualTo(pageEn.sourceRef()).isEqualTo(articleTr.sourceRef());
        assertThat(articleEn).isNotEqualTo(pageEn).isNotEqualTo(articleTr);
        assertThat(articleEn).isEqualTo(key(DiscoverableResourceType.ARTICLE, DiscoveryLanguage.EN));
    }

    private static DiscoverableResource listedResource(DiscoverableResourceType type) {
        return resource(values -> values
                .withKey(key(type, DiscoveryLanguage.EN))
                .withEligibility(DiscoverableResourceEligibilityPolicy.listedFor(type)));
    }

    private static DiscoverableResource resource(UnaryOperator<ResourceValues> mutation) {
        ResourceValues values = mutation.apply(ResourceValues.baseline());
        DiscoverableResourceEligibility eligibility = values.eligibility();
        return DiscoverableResource.createCurrent(
                values.id(),
                values.key(),
                values.publicUrl(),
                values.canonicalUrl(),
                values.title(),
                values.summary(),
                eligibility.indexingPolicy(),
                eligibility.searchEligibility(),
                eligibility.sitemapEligibility(),
                eligibility.feedEligibility(),
                SocialPreviewProfile.empty(),
                null,
                null,
                values.searchText(),
                CREATED_AT,
                Version.initial());
    }

    private static DiscoverableResourceKey key(DiscoverableResourceType type, DiscoveryLanguage language) {
        return new DiscoverableResourceKey(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                new SourceEntityId(UUID.fromString("d4c57198-c3d4-477f-839b-7b48848628ec")),
                type,
                language,
                RoutePurpose.DETAIL);
    }

    private record ResourceValues(
            DiscoverableResourceId id,
            DiscoverableResourceKey key,
            PublicUrl publicUrl,
            CanonicalUrl canonicalUrl,
            ResourceTitle title,
            ResourceSummary summary,
            SearchText searchText,
            DiscoverableResourceEligibility eligibility) {
        static ResourceValues baseline() {
            return new ResourceValues(
                    DiscoverableResourceId.random(),
                    DiscoverableResourceTest.key(DiscoverableResourceType.ARTICLE, DiscoveryLanguage.EN),
                    new PublicUrl("/en/articles/baseline"),
                    new CanonicalUrl("https://persefonia.dev/en/articles/baseline"),
                    new ResourceTitle("Baseline"),
                    new ResourceSummary("Baseline summary"),
                    new SearchText("Baseline search text"),
                    DiscoverableResourceEligibilityPolicy.listedFor(DiscoverableResourceType.ARTICLE));
        }

        ResourceValues withId(DiscoverableResourceId value) {
            return new ResourceValues(value, key, publicUrl, canonicalUrl, title, summary, searchText, eligibility);
        }

        ResourceValues withKey(DiscoverableResourceKey value) {
            return new ResourceValues(id, value, publicUrl, canonicalUrl, title, summary, searchText, eligibility);
        }

        ResourceValues withPublicUrl(PublicUrl value) {
            return new ResourceValues(id, key, value, canonicalUrl, title, summary, searchText, eligibility);
        }

        ResourceValues withCanonicalUrl(CanonicalUrl value) {
            return new ResourceValues(id, key, publicUrl, value, title, summary, searchText, eligibility);
        }

        ResourceValues withTitle(ResourceTitle value) {
            return new ResourceValues(id, key, publicUrl, canonicalUrl, value, summary, searchText, eligibility);
        }

        ResourceValues withSummary(ResourceSummary value) {
            return new ResourceValues(id, key, publicUrl, canonicalUrl, title, value, searchText, eligibility);
        }

        ResourceValues withSearchText(SearchText value) {
            return new ResourceValues(id, key, publicUrl, canonicalUrl, title, summary, value, eligibility);
        }

        ResourceValues withEligibility(DiscoverableResourceEligibility value) {
            return new ResourceValues(id, key, publicUrl, canonicalUrl, title, summary, searchText, value);
        }
    }
}
