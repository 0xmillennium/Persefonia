package dev.persefonia.discovery.application.projection;

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
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class DiscoveryProjectionContractTest {
    @Test
    void tagProjectionContractValuesAreAccepted() {
        assertThat(SourceContext.valueOf("TAXONOMY")).isEqualTo(SourceContext.TAXONOMY);
        assertThat(SourceType.valueOf("TAG")).isEqualTo(SourceType.TAG);
        assertThat(DiscoverableResourceType.valueOf("TAG")).isEqualTo(DiscoverableResourceType.TAG);
        assertThat(RoutePurpose.valueOf("TAG_PAGE")).isEqualTo(RoutePurpose.TAG_PAGE);
    }

    @Test
    void seriesProjectionContractValuesAreAccepted() {
        assertThat(SourceContext.valueOf("CONTENT_PUBLISHING")).isEqualTo(SourceContext.CONTENT_PUBLISHING);
        assertThat(SourceType.valueOf("SERIES")).isEqualTo(SourceType.SERIES);
        assertThat(DiscoverableResourceType.valueOf("SERIES")).isEqualTo(DiscoverableResourceType.SERIES);
        assertThat(RoutePurpose.valueOf("SERIES_PAGE")).isEqualTo(RoutePurpose.SERIES_PAGE);
    }

    @Test
    void seriesPageProjectionUsesNoindexAndIneligibleFlags() {
        DiscoverableResourceProjectionInput input = new DiscoverableResourceProjectionInput(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.SERIES,
                sourceEntityId(),
                DiscoverableResourceType.SERIES,
                RoutePurpose.SERIES_PAGE,
                DiscoveryLanguage.EN,
                new PublicUrl("/en/series/spring-boot-notes"),
                new CanonicalUrl("https://persefonia.dev/en/series/spring-boot-notes"),
                "Spring Boot Notes",
                "Ordered Spring Boot notes",
                IndexingPolicy.NO_INDEX,
                DiscoveryEligibility.NOT_ELIGIBLE,
                DiscoveryEligibility.NOT_ELIGIBLE,
                DiscoveryEligibility.NOT_ELIGIBLE,
                null,
                null,
                null,
                null,
                null,
                "Spring Boot Notes\nOrdered Spring Boot notes");

        assertThat(input)
                .extracting(
                        DiscoverableResourceProjectionInput::sourceContext,
                        DiscoverableResourceProjectionInput::sourceType,
                        DiscoverableResourceProjectionInput::resourceType,
                        DiscoverableResourceProjectionInput::routePurpose,
                        DiscoverableResourceProjectionInput::indexingPolicy,
                        DiscoverableResourceProjectionInput::searchEligibility,
                        DiscoverableResourceProjectionInput::sitemapEligibility,
                        DiscoverableResourceProjectionInput::feedEligibility)
                .containsExactly(
                        SourceContext.CONTENT_PUBLISHING,
                        SourceType.SERIES,
                        DiscoverableResourceType.SERIES,
                        RoutePurpose.SERIES_PAGE,
                        IndexingPolicy.NO_INDEX,
                        DiscoveryEligibility.NOT_ELIGIBLE,
                        DiscoveryEligibility.NOT_ELIGIBLE,
                        DiscoveryEligibility.NOT_ELIGIBLE);
    }

    @Test
    void projectionInputAcceptsValidCurrentProjectionInput() {
        assertThat(validInput(UnaryOperator.identity()))
                .extracting(
                        DiscoverableResourceProjectionInput::sourceContext,
                        DiscoverableResourceProjectionInput::resourceType,
                        DiscoverableResourceProjectionInput::routePurpose,
                        DiscoverableResourceProjectionInput::publicUrl,
                        DiscoverableResourceProjectionInput::canonicalUrl)
                .containsExactly(
                        SourceContext.CONTENT_PUBLISHING,
                        DiscoverableResourceType.ARTICLE,
                        RoutePurpose.DETAIL,
                        new PublicUrl("/en/articles/contract"),
                        new CanonicalUrl("https://persefonia.dev/en/articles/contract"));
    }

    @Test
    void projectionInputRequiresSourceReference() {
        assertThatThrownBy(() -> validInput(values -> values.withSourceContext(null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sourceContext");
        assertThatThrownBy(() -> validInput(values -> values.withSourceType(null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sourceType");
        assertThatThrownBy(() -> validInput(values -> values.withSourceEntityId(null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sourceEntityId");
    }

    @Test
    void projectionInputRequiresProjectionIdentityAndPolicyFields() {
        assertThatThrownBy(() -> validInput(values -> values.withPublicUrl(null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("publicUrl");
        assertThatThrownBy(() -> validInput(values -> values.withCanonicalUrl(null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("canonicalUrl");
    }

    @Test
    void projectionInputRejectsBlankRequiredText() {
        assertThatThrownBy(() -> validInput(values -> values.withTitle(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title must not be blank");
        assertThatThrownBy(() -> validInput(values -> values.withSummary("\t")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("summary must not be blank");
        assertThatThrownBy(() -> validInput(values -> values.withSearchText("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("searchText must not be blank");
    }

    @Test
    void removeCommandRequiresCompleteSourceReference() {
        SourceEntityId sourceEntityId = sourceEntityId();

        assertThat(new RemoveDiscoverableResourceCommand(
                        SourceContext.CONTENT_PUBLISHING,
                        SourceType.CONTENT_ITEM,
                        sourceEntityId))
                .extracting(
                        RemoveDiscoverableResourceCommand::sourceContext,
                        RemoveDiscoverableResourceCommand::sourceType,
                        RemoveDiscoverableResourceCommand::sourceEntityId)
                .containsExactly(SourceContext.CONTENT_PUBLISHING, SourceType.CONTENT_ITEM, sourceEntityId);

        assertThatThrownBy(() -> new RemoveDiscoverableResourceCommand(
                        null, SourceType.CONTENT_ITEM, sourceEntityId))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RemoveDiscoverableResourceCommand(
                        SourceContext.CONTENT_PUBLISHING, null, sourceEntityId))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RemoveDiscoverableResourceCommand(
                        SourceContext.CONTENT_PUBLISHING, SourceType.CONTENT_ITEM, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void projectionResultExposesNoAggregateOrEntity() {
        assertThat(DiscoverableResourceProjectionResult.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(
                        DiscoverableResourceProjectionResult.Updated.class,
                        DiscoverableResourceProjectionResult.Removed.class,
                        DiscoverableResourceProjectionResult.Noop.class,
                        DiscoverableResourceProjectionResult.Rejected.class);
        assertThat(DiscoverableResourceProjectionResult.Updated.class.getRecordComponents()).isEmpty();
        assertThat(DiscoverableResourceProjectionResult.Removed.class.getRecordComponents()).isEmpty();
        assertThat(DiscoverableResourceProjectionResult.Noop.class.getRecordComponents()).isEmpty();
        assertThat(DiscoverableResourceProjectionResult.Rejected.class.getRecordComponents())
                .singleElement()
                .extracting(component -> component.getType())
                .isEqualTo(DiscoverableResourceProjectionResult.Reason.class);
    }

    private static DiscoverableResourceProjectionInput validInput(UnaryOperator<ProjectionValues> mutation) {
        ProjectionValues values = mutation.apply(new ProjectionValues(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                sourceEntityId(),
                new PublicUrl("/en/articles/contract"),
                new CanonicalUrl("https://persefonia.dev/en/articles/contract"),
                "Contract title",
                "Contract summary",
                "Contract searchable text"));

        return new DiscoverableResourceProjectionInput(
                values.sourceContext(),
                values.sourceType(),
                values.sourceEntityId(),
                DiscoverableResourceType.ARTICLE,
                RoutePurpose.DETAIL,
                DiscoveryLanguage.EN,
                values.publicUrl(),
                values.canonicalUrl(),
                values.title(),
                values.summary(),
                IndexingPolicy.INDEX,
                DiscoveryEligibility.ELIGIBLE,
                DiscoveryEligibility.ELIGIBLE,
                DiscoveryEligibility.ELIGIBLE,
                null,
                null,
                null,
                null,
                null,
                values.searchText());
    }

    private static SourceEntityId sourceEntityId() {
        return new SourceEntityId(UUID.fromString("d4c57198-c3d4-477f-839b-7b48848628ec"));
    }

    private record ProjectionValues(
            SourceContext sourceContext,
            SourceType sourceType,
            SourceEntityId sourceEntityId,
            PublicUrl publicUrl,
            CanonicalUrl canonicalUrl,
            String title,
            String summary,
            String searchText) {
        ProjectionValues withSourceContext(SourceContext value) {
            return new ProjectionValues(value, sourceType, sourceEntityId, publicUrl, canonicalUrl, title, summary, searchText);
        }

        ProjectionValues withSourceType(SourceType value) {
            return new ProjectionValues(sourceContext, value, sourceEntityId, publicUrl, canonicalUrl, title, summary, searchText);
        }

        ProjectionValues withSourceEntityId(SourceEntityId value) {
            return new ProjectionValues(sourceContext, sourceType, value, publicUrl, canonicalUrl, title, summary, searchText);
        }

        ProjectionValues withPublicUrl(PublicUrl value) {
            return new ProjectionValues(sourceContext, sourceType, sourceEntityId, value, canonicalUrl, title, summary, searchText);
        }

        ProjectionValues withCanonicalUrl(CanonicalUrl value) {
            return new ProjectionValues(sourceContext, sourceType, sourceEntityId, publicUrl, value, title, summary, searchText);
        }

        ProjectionValues withTitle(String value) {
            return new ProjectionValues(sourceContext, sourceType, sourceEntityId, publicUrl, canonicalUrl, value, summary, searchText);
        }

        ProjectionValues withSummary(String value) {
            return new ProjectionValues(sourceContext, sourceType, sourceEntityId, publicUrl, canonicalUrl, title, value, searchText);
        }

        ProjectionValues withSearchText(String value) {
            return new ProjectionValues(sourceContext, sourceType, sourceEntityId, publicUrl, canonicalUrl, title, summary, value);
        }
    }
}
