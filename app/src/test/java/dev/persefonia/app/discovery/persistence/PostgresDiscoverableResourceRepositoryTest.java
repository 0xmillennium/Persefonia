package dev.persefonia.app.discovery.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.domain.DiscoverableResource;
import dev.persefonia.discovery.domain.DiscoverableResourceId;
import dev.persefonia.discovery.domain.OpenGraphDescription;
import dev.persefonia.discovery.domain.OpenGraphTitle;
import dev.persefonia.discovery.domain.ResourceSummary;
import dev.persefonia.discovery.domain.ResourceTitle;
import dev.persefonia.discovery.domain.SearchText;
import dev.persefonia.discovery.domain.SocialPreviewProfile;
import org.junit.jupiter.api.Test;

class PostgresDiscoverableResourceRepositoryTest extends DiscoveryRepositoryTestDatabase {
    @Test
    void savesAndFindsByEverySupportedLookup() {
        DiscoverableResource resource = DiscoveryRepositoryFixtures.resource("round-trip");

        DiscoverableResource saved = resources.save(resource);

        assertThat(resources.findById(saved.id())).hasValueSatisfying(
                actual -> assertThat(actual).usingRecursiveComparison().isEqualTo(saved));
        assertThat(resources.findByKey(saved.key())).hasValueSatisfying(
                actual -> assertThat(actual).usingRecursiveComparison().isEqualTo(saved));
        assertThat(resources.findByPublicUrl(saved.publicUrl())).hasValueSatisfying(
                actual -> assertThat(actual).usingRecursiveComparison().isEqualTo(saved));
        assertThat(resources.findBySourceRef(saved.sourceRef()))
                .extracting(found -> found.id())
                .containsExactly(saved.id());
    }

    @Test
    void sourceReferenceMayOwnMultipleCurrentResources() {
        DiscoverableResource article = resources.save(DiscoveryRepositoryFixtures.resource("article"));
        DiscoverableResource page = resources.save(DiscoveryRepositoryFixtures.resource(
                DiscoverableResourceId.random(), "page", DiscoverableResourceType.PAGE));

        assertThat(resources.findBySourceRef(DiscoveryRepositoryFixtures.SOURCE_REF))
                .extracting(found -> found.id())
                .containsExactlyInAnyOrder(article.id(), page.id());
    }

    @Test
    void replaceByKeyPreservesIdentityAndCreationTimeWhileReplacingProjectionAndAdvancingVersion() {
        DiscoverableResource original = resources.replaceByKey(DiscoveryRepositoryFixtures.resource("original"));
        DiscoverableResource replacementInput = DiscoveryRepositoryFixtures.resource(
                DiscoverableResourceId.random(), "ignored-id", DiscoverableResourceType.ARTICLE)
                .replaceCurrentProjection(
                        new PublicUrl("/replacement"),
                        new dev.persefonia.discovery.application.contract.CanonicalUrl("https://example.test/replacement"),
                        new ResourceTitle("Replacement title"),
                        new ResourceSummary("Replacement summary"),
                        IndexingPolicy.NO_INDEX,
                        DiscoveryEligibility.NOT_ELIGIBLE,
                        DiscoveryEligibility.NOT_ELIGIBLE,
                        DiscoveryEligibility.NOT_ELIGIBLE,
                        new SocialPreviewProfile(
                                new OpenGraphTitle("Replacement OG"),
                                new OpenGraphDescription("Replacement OG description"),
                                null),
                        null,
                        DiscoveryRepositoryFixtures.NOW,
                        new SearchText("Replacement search"));

        DiscoverableResource replaced = resources.replaceByKey(replacementInput);

        assertThat(replaced.id()).isEqualTo(original.id());
        assertThat(replaced.createdAt()).isEqualTo(original.createdAt());
        assertThat(replaced.version().value()).isEqualTo(original.version().value() + 1);
        assertThat(replaced.publicUrl().value()).isEqualTo("/replacement");
        assertThat(replaced.canonicalUrl().value()).isEqualTo("https://example.test/replacement");
        assertThat(replaced.title().value()).isEqualTo("Replacement title");
        assertThat(replaced.summary().value()).isEqualTo("Replacement summary");
        assertThat(replaced.indexingPolicy()).isEqualTo(IndexingPolicy.NO_INDEX);
        assertThat(replaced.openGraph().imageAssetId()).isNull();
        assertThat(replaced.searchText().value()).isEqualTo("Replacement search");
        assertThat(resources.findByPublicUrl(new PublicUrl("/original"))).isEmpty();
    }

    @Test
    void removeBySourceRefDeletesCurrentRowsAndIsIdempotent() {
        DiscoverableResource resource = resources.save(DiscoveryRepositoryFixtures.resource("remove"));

        assertThat(resources.removeBySourceRef(resource.sourceRef())).isEqualTo(1);
        assertThat(resources.removeBySourceRef(resource.sourceRef())).isZero();
        assertThat(resources.findByPublicUrl(resource.publicUrl())).isEmpty();
    }
}
