package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.discovery.ContentDiscoveryRedirectFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.redirect.CreateRedirectRuleCommand;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContentDiscoveryRedirectFactoryTest {
    private final ContentDiscoveryRedirectFactory factory =
            new ContentDiscoveryRedirectFactory(new ContentPublicRouteFactory());

    @Test
    void publishedPublicSlugChangeCreatesPermanentSlugChangedRedirect() {
        var item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        Slug previousSlug = item.slug().orElseThrow();
        item.changeSlug(Slug.of("updated-route"), NOW);

        var redirect = factory.slugChangedRedirect(
                item, true, ContentVisibility.PUBLIC, Optional.of(previousSlug));

        assertThat(redirect).hasValueSatisfying(command -> assertSlugChangedRedirect(command, item.id().value()));
    }

    @Test
    void publishedUnlistedSlugChangeCreatesPermanentSlugChangedRedirect() {
        var item = ContentItemTestFixtures.published(ContentVisibility.UNLISTED);
        Slug previousSlug = item.slug().orElseThrow();
        item.changeSlug(Slug.of("updated-route"), NOW);

        var redirect = factory.slugChangedRedirect(
                item, true, ContentVisibility.UNLISTED, Optional.of(previousSlug));

        assertThat(redirect).hasValueSatisfying(command -> assertSlugChangedRedirect(command, item.id().value()));
    }

    @Test
    void publishedPrivateSlugChangeCreatesNoRedirect() {
        var item = ContentItemTestFixtures.published(ContentVisibility.PRIVATE);
        Slug previousSlug = item.slug().orElseThrow();
        item.changeSlug(Slug.of("updated-route"), NOW);

        assertThat(factory.slugChangedRedirect(
                item, true, ContentVisibility.PRIVATE, Optional.of(previousSlug)))
                .isEmpty();
    }

    @Test
    void previousNonPublishedStateCreatesNoRedirect() {
        var item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        Slug previousSlug = item.slug().orElseThrow();
        item.changeSlug(Slug.of("updated-route"), NOW);

        assertThat(factory.slugChangedRedirect(
                item, false, ContentVisibility.PUBLIC, Optional.of(previousSlug)))
                .isEmpty();
    }

    @Test
    void currentSavedStateNotDirectUrlEligibleCreatesNoRedirect() {
        var item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        Slug previousSlug = item.slug().orElseThrow();
        item.changeSlug(Slug.of("updated-route"), NOW);
        item.changeVisibility(ContentVisibility.PRIVATE, NOW);

        assertThat(factory.slugChangedRedirect(
                item, true, ContentVisibility.PUBLIC, Optional.of(previousSlug)))
                .isEmpty();
    }

    @Test
    void sameSlugCreatesNoRedirect() {
        var item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);

        assertThat(factory.slugChangedRedirect(
                item, true, ContentVisibility.PUBLIC, item.slug()))
                .isEmpty();
    }

    private static void assertSlugChangedRedirect(CreateRedirectRuleCommand command, UUID contentId) {
        assertThat(command.sourceUrl().value()).isEqualTo("/en/articles/content-baseline");
        assertThat(command.targetUrl().value()).isEqualTo("/en/articles/updated-route");
        assertThat(command.statusCode()).isEqualTo(RedirectStatusCode.MOVED_PERMANENTLY_301);
        assertThat(command.reason()).isEqualTo(RedirectReason.SLUG_CHANGED);
        assertThat(command.sourceContext()).isEqualTo(SourceContext.CONTENT_PUBLISHING);
        assertThat(command.sourceType()).isEqualTo(SourceType.CONTENT_ITEM);
        assertThat(command.sourceEntityId().value()).isEqualTo(contentId);
    }
}
