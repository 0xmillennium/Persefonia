package dev.persefonia.contentpublishing.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.publicview.ContentPublicExposurePolicy;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import org.junit.jupiter.api.Test;

class ContentPublicExposurePolicyTest {
    private final ContentPublicExposurePolicy policy = new ContentPublicExposurePolicy();

    @Test
    void publishedPublicContentIsListedAndPageIsNotFeedEligible() {
        assertThat(policy.snapshot(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentType.ARTICLE))
                .extracting("directReachable", "listed", "sitemapEligible", "feedEligible")
                .containsExactly(true, true, true, true);
        assertThat(policy.snapshot(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentType.PAGE).feedEligible())
                .isFalse();
    }

    @Test
    void unlistedIsDirectOnlyAndPrivateOrNonPublishedHasNoExposure() {
        assertThat(policy.snapshot(ContentStatus.PUBLISHED, ContentVisibility.UNLISTED, ContentType.NOTE))
                .extracting("directReachable", "listed", "sitemapEligible", "feedEligible")
                .containsExactly(true, false, false, false);
        assertThat(policy.snapshot(ContentStatus.PUBLISHED, ContentVisibility.PRIVATE, ContentType.NOTE))
                .isEqualTo(dev.persefonia.contentpublishing.application.publicview.ContentPublicExposureSnapshot.none());
        assertThat(policy.snapshot(ContentStatus.ARCHIVED, ContentVisibility.PUBLIC, ContentType.NOTE))
                .isEqualTo(dev.persefonia.contentpublishing.application.publicview.ContentPublicExposureSnapshot.none());
    }
}
