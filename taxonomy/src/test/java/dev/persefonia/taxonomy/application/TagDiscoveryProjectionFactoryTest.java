package dev.persefonia.taxonomy.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.taxonomy.application.discovery.TagDiscoveryProjectionFactory;
import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagDescription;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagName;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TagDiscoveryProjectionFactoryTest {
    @Test
    void tagPageProjectionUsesBothLanguagesNoindexAndIneligibleFlags() {
        var projections = new TagDiscoveryProjectionFactory("https://example.test/").projectionsFor(tag());

        assertThat(projections).hasSize(2);
        assertThat(projections).extracting(input -> input.language())
                .containsExactly(DiscoveryLanguage.TR, DiscoveryLanguage.EN);
        assertThat(projections).allSatisfy(input -> {
            assertThat(input.sourceContext()).isEqualTo(SourceContext.TAXONOMY);
            assertThat(input.sourceType()).isEqualTo(SourceType.TAG);
            assertThat(input.resourceType()).isEqualTo(DiscoverableResourceType.TAG);
            assertThat(input.routePurpose()).isEqualTo(RoutePurpose.TAG_PAGE);
            assertThat(input.indexingPolicy()).isEqualTo(IndexingPolicy.NO_INDEX);
            assertThat(input.searchEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
            assertThat(input.sitemapEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
            assertThat(input.feedEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
        });
        assertThat(projections).extracting(input -> input.publicUrl().value())
                .containsExactly("/tr/tags/spring", "/en/tags/spring");
        assertThat(projections).extracting(input -> input.canonicalUrl().value())
                .containsExactly("https://example.test/tr/tags/spring", "https://example.test/en/tags/spring");
    }

    private static Tag tag() {
        return Tag.create(
                TagId.newId(),
                TagName.of("Spring"),
                NormalizedTagName.ofCanonical("spring"),
                TagSlug.ofCanonical("spring"),
                TagDescription.ofNullable("Spring content"),
                Instant.parse("2026-06-15T10:00:00Z"));
    }
}
