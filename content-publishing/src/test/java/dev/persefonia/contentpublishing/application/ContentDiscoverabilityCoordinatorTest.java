package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.discovery.ConfiguredContentCanonicalUrlFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentDiscoverabilityCoordinator;
import dev.persefonia.contentpublishing.application.discovery.ContentDiscoveryProjectionFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentDiscoveryRedirectFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.exception.ContentDiscoverySynchronizationException;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionInput;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.discovery.application.projection.RemoveDiscoverableResourceCommand;
import dev.persefonia.discovery.application.redirect.CreateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleChangeSummary;
import dev.persefonia.discovery.domain.RedirectRuleId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContentDiscoverabilityCoordinatorTest {
    private final RecordingDiscoveryPorts ports = new RecordingDiscoveryPorts();
    private final ContentDiscoverabilityCoordinator coordinator = coordinator(ports);

    @Test
    void publicPublishedContentCreatesCurrentDiscoveryProjection() {
        var item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);

        coordinator.syncPublishedContent(item, false, ContentVisibility.PUBLIC, Optional.empty());

        assertThat(ports.updates).singleElement().satisfies(input -> {
            assertThat(input.publicUrl().value()).isEqualTo("/en/articles/content-baseline");
            assertThat(input.canonicalUrl().value()).isEqualTo("https://persefonia.test/en/articles/content-baseline");
            assertThat(input.indexingPolicy()).isEqualTo(IndexingPolicy.INDEX);
            assertThat(input.searchEligibility()).isEqualTo(DiscoveryEligibility.ELIGIBLE);
            assertThat(input.sitemapEligibility()).isEqualTo(DiscoveryEligibility.ELIGIBLE);
            assertThat(input.feedEligibility()).isEqualTo(DiscoveryEligibility.ELIGIBLE);
            assertThat(input.title()).isEqualTo("Content baseline");
            assertThat(input.summary()).isEqualTo("A concise summary for the content baseline.");
        });
        assertThat(ports.removals).isEmpty();
    }

    @Test
    void unlistedPublishedContentCreatesNoindexNonDiscoverableProjection() {
        var item = ContentItemTestFixtures.published(ContentVisibility.UNLISTED);

        coordinator.syncPublishedContent(item, false, ContentVisibility.UNLISTED, Optional.empty());

        assertThat(ports.updates).singleElement().satisfies(input -> {
            assertThat(input.indexingPolicy()).isEqualTo(IndexingPolicy.NO_INDEX);
            assertThat(input.searchEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
            assertThat(input.sitemapEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
            assertThat(input.feedEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
        });
    }

    @Test
    void privatePublishedContentRemovesCurrentDiscoveryProjection() {
        var item = ContentItemTestFixtures.published(ContentVisibility.PRIVATE);

        coordinator.syncPublishedContent(item, false, ContentVisibility.PRIVATE, Optional.empty());

        assertThat(ports.updates).isEmpty();
        assertThat(ports.removals).singleElement()
                .satisfies(command -> assertThat(command.sourceEntityId().value()).isEqualTo(item.id().value()));
    }

    @Test
    void unpublishOrArchiveRemovesCurrentDiscoveryProjection() {
        var item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        item.unpublish(NOW);

        coordinator.removeContent(item);

        assertThat(ports.removals).singleElement()
                .satisfies(command -> assertThat(command.sourceEntityId().value()).isEqualTo(item.id().value()));
    }

    @Test
    void publishedPublicSlugChangeCreatesPermanentSlugChangedRedirect() {
        var item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        Slug previousSlug = item.slug().orElseThrow();
        item.changeSlug(Slug.of("updated-route"), NOW);
        item.changeMetadata(ContentMetadata.withCanonicalPath(CanonicalPath.of("/articles/updated-route")), NOW);

        coordinator.syncContentUpdate(item, item.status(), ContentVisibility.PUBLIC, Optional.of(previousSlug));

        assertThat(ports.redirects).singleElement().satisfies(command -> {
            assertThat(command.sourceUrl().value()).isEqualTo("/en/articles/content-baseline");
            assertThat(command.targetUrl().value()).isEqualTo("/en/articles/updated-route");
            assertThat(command.statusCode()).isEqualTo(RedirectStatusCode.MOVED_PERMANENTLY_301);
            assertThat(command.reason()).isEqualTo(RedirectReason.SLUG_CHANGED);
            assertThat(command.sourceContext()).isEqualTo(SourceContext.CONTENT_PUBLISHING);
            assertThat(command.sourceType()).isEqualTo(SourceType.CONTENT_ITEM);
            assertThat(command.sourceEntityId().value()).isEqualTo(item.id().value());
        });
    }

    @Test
    void publishedUnlistedSlugChangeCreatesPermanentSlugChangedRedirect() {
        var item = ContentItemTestFixtures.published(ContentVisibility.UNLISTED);
        Slug previousSlug = item.slug().orElseThrow();
        item.changeSlug(Slug.of("updated-route"), NOW);

        coordinator.syncContentUpdate(item, item.status(), ContentVisibility.UNLISTED, Optional.of(previousSlug));

        assertThat(ports.redirects).singleElement().satisfies(command -> {
            assertThat(command.sourceUrl().value()).isEqualTo("/en/articles/content-baseline");
            assertThat(command.targetUrl().value()).isEqualTo("/en/articles/updated-route");
            assertThat(command.statusCode()).isEqualTo(RedirectStatusCode.MOVED_PERMANENTLY_301);
            assertThat(command.reason()).isEqualTo(RedirectReason.SLUG_CHANGED);
        });
    }

    @Test
    void publishedPrivateSlugChangeDoesNotCreateRedirect() {
        var item = ContentItemTestFixtures.published(ContentVisibility.PRIVATE);
        Slug previousSlug = item.slug().orElseThrow();
        item.changeSlug(Slug.of("updated-route"), NOW);

        coordinator.syncContentUpdate(item, item.status(), ContentVisibility.PRIVATE, Optional.of(previousSlug));

        assertThat(ports.redirects).isEmpty();
    }

    @Test
    void sameSlugUpdateDoesNotCreateRedirect() {
        var item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);

        coordinator.syncContentUpdate(item, item.status(), ContentVisibility.PUBLIC, item.slug());

        assertThat(ports.redirects).isEmpty();
    }

    @Test
    void rejectedRedirectResultFailsCommandPath() {
        ports.redirectResult = new RedirectRuleCreationResult.Rejected(
                RedirectRuleCreationResult.Reason.DUPLICATE_ACTIVE_SOURCE);
        var item = ContentItemTestFixtures.published(ContentVisibility.UNLISTED);
        Slug previousSlug = item.slug().orElseThrow();
        item.changeSlug(Slug.of("updated-route"), NOW);

        assertThatThrownBy(() -> coordinator.syncContentUpdate(
                        item, item.status(), ContentVisibility.UNLISTED, Optional.of(previousSlug)))
                .isInstanceOf(ContentDiscoverySynchronizationException.class)
                .hasMessageContaining("DUPLICATE_ACTIVE_SOURCE");
    }

    @Test
    void rejectedDiscoveryResultFailsCommandPath() {
        ports.projectionResult = new DiscoverableResourceProjectionResult.Rejected(
                DiscoverableResourceProjectionResult.Reason.CONFLICT);
        var item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);

        assertThatThrownBy(() -> coordinator.syncPublishedContent(
                        item, false, ContentVisibility.PUBLIC, Optional.empty()))
                .isInstanceOf(ContentDiscoverySynchronizationException.class)
                .hasMessageContaining("CONFLICT");
    }

    private static ContentDiscoverabilityCoordinator coordinator(RecordingDiscoveryPorts ports) {
        ContentPublicRouteFactory routeFactory = new ContentPublicRouteFactory();
        return new ContentDiscoverabilityCoordinator(
                ports::update,
                ports::remove,
                ports::create,
                new ContentDiscoveryProjectionFactory(
                        routeFactory,
                        new ConfiguredContentCanonicalUrlFactory("https://persefonia.test/")),
                new ContentDiscoveryRedirectFactory(routeFactory));
    }

    private static final class RecordingDiscoveryPorts {
        private final List<DiscoverableResourceProjectionInput> updates = new ArrayList<>();
        private final List<RemoveDiscoverableResourceCommand> removals = new ArrayList<>();
        private final List<CreateRedirectRuleCommand> redirects = new ArrayList<>();
        private DiscoverableResourceProjectionResult projectionResult = new DiscoverableResourceProjectionResult.Updated();
        private RedirectRuleCreationResult redirectResult = new RedirectRuleCreationResult.Created(
                new RedirectRuleChangeSummary(
                        RedirectRuleId.random(),
                        new dev.persefonia.discovery.application.contract.PublicUrl("/old"),
                        new dev.persefonia.discovery.application.contract.PublicUrl("/new"),
                        dev.persefonia.discovery.application.contract.RedirectStatusCode.MOVED_PERMANENTLY_301,
                        dev.persefonia.discovery.application.contract.RedirectReason.SLUG_CHANGED));

        private DiscoverableResourceProjectionResult update(DiscoverableResourceProjectionInput input) {
            updates.add(input);
            return projectionResult;
        }

        private DiscoverableResourceProjectionResult remove(RemoveDiscoverableResourceCommand command) {
            removals.add(command);
            return new DiscoverableResourceProjectionResult.Removed();
        }

        private RedirectRuleCreationResult create(CreateRedirectRuleCommand command) {
            redirects.add(command);
            return redirectResult;
        }
    }
}
