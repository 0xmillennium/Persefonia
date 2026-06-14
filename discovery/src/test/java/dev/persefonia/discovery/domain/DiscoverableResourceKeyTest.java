package dev.persefonia.discovery.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoverableResourceKeyTest {
    private static final SourceEntityId SOURCE_ENTITY_ID =
            new SourceEntityId(UUID.fromString("d4c57198-c3d4-477f-839b-7b48848628ec"));

    @Test
    void keyRequiresEveryPart() {
        assertThatThrownBy(() -> key(null, SourceType.CONTENT_ITEM, SOURCE_ENTITY_ID,
                        DiscoverableResourceType.ARTICLE, DiscoveryLanguage.EN, RoutePurpose.DETAIL))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> key(SourceContext.CONTENT_PUBLISHING, null, SOURCE_ENTITY_ID,
                        DiscoverableResourceType.ARTICLE, DiscoveryLanguage.EN, RoutePurpose.DETAIL))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> key(SourceContext.CONTENT_PUBLISHING, SourceType.CONTENT_ITEM, null,
                        DiscoverableResourceType.ARTICLE, DiscoveryLanguage.EN, RoutePurpose.DETAIL))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> key(SourceContext.CONTENT_PUBLISHING, SourceType.CONTENT_ITEM, SOURCE_ENTITY_ID,
                        null, DiscoveryLanguage.EN, RoutePurpose.DETAIL))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> key(SourceContext.CONTENT_PUBLISHING, SourceType.CONTENT_ITEM, SOURCE_ENTITY_ID,
                        DiscoverableResourceType.ARTICLE, null, RoutePurpose.DETAIL))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> key(SourceContext.CONTENT_PUBLISHING, SourceType.CONTENT_ITEM, SOURCE_ENTITY_ID,
                        DiscoverableResourceType.ARTICLE, DiscoveryLanguage.EN, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sourceReferenceRequiresEveryPart() {
        assertThatThrownBy(() -> new SourceEntityRef(null, SourceType.CONTENT_ITEM, SOURCE_ENTITY_ID))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SourceEntityRef(SourceContext.CONTENT_PUBLISHING, null, SOURCE_ENTITY_ID))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SourceEntityRef(SourceContext.CONTENT_PUBLISHING, SourceType.CONTENT_ITEM, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static DiscoverableResourceKey key(
            SourceContext sourceContext,
            SourceType sourceType,
            SourceEntityId sourceEntityId,
            DiscoverableResourceType resourceType,
            DiscoveryLanguage language,
            RoutePurpose routePurpose) {
        return new DiscoverableResourceKey(
                sourceContext, sourceType, sourceEntityId, resourceType, language, routePurpose);
    }
}
