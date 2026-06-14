package dev.persefonia.discovery.application.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoveryRouteContractTest {
    @Test
    void publicRouteLookupAcceptsPathLikePublicUrl() {
        PublicUrl publicUrl = new PublicUrl("/tr/notes/yol");

        assertThat(new PublicRouteLookup(publicUrl).publicUrl()).isEqualTo(publicUrl);
    }

    @Test
    void publicRouteLookupRejectsInvalidPublicUrl() {
        assertThatThrownBy(() -> new PublicUrl(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicUrl("https://persefonia.dev/en/articles/contract"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicUrl("en/articles/contract"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicRouteLookup(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void canonicalUrlRequiresAnAbsoluteWhitespaceFreeUri() {
        assertThat(new CanonicalUrl("https://persefonia.dev/en/articles/contract").value())
                .isEqualTo("https://persefonia.dev/en/articles/contract");
        assertThatThrownBy(() -> new CanonicalUrl("/en/articles/contract"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CanonicalUrl("https://persefonia.dev/an article"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void foundExposesSourceReferenceAndRouteMetadataOnly() {
        PublicRouteResolution.Found found = new PublicRouteResolution.Found(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                sourceEntityId(),
                DiscoverableResourceType.ARTICLE,
                RoutePurpose.DETAIL,
                DiscoveryLanguage.EN,
                new PublicUrl("/en/articles/contract"),
                new CanonicalUrl("https://persefonia.dev/en/articles/contract"),
                IndexingPolicy.INDEX);

        assertThat(PublicRouteResolution.Found.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly(
                        "sourceContext",
                        "sourceType",
                        "sourceEntityId",
                        "resourceType",
                        "routePurpose",
                        "language",
                        "publicUrl",
                        "canonicalUrl",
                        "indexingPolicy");
        assertThat(found.sourceEntityId()).isEqualTo(sourceEntityId());
    }

    @Test
    void redirectExposesStatusAndTargetOnly() {
        PublicUrl targetUrl = new PublicUrl("/en/articles/current");
        PublicRouteResolution.Redirect redirect =
                new PublicRouteResolution.Redirect(RedirectStatusCode.MOVED_PERMANENTLY_301, targetUrl);

        assertThat(PublicRouteResolution.Redirect.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("statusCode", "targetUrl");
        assertThat(redirect.targetUrl()).isEqualTo(targetUrl);
    }

    @Test
    void notFoundExposesNoReasonOrDetails() {
        assertThat(PublicRouteResolution.NotFound.class.getRecordComponents()).isEmpty();
    }

    private static SourceEntityId sourceEntityId() {
        return new SourceEntityId(UUID.fromString("d4c57198-c3d4-477f-839b-7b48848628ec"));
    }
}
