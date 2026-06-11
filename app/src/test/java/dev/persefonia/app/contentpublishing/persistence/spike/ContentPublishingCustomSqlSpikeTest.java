package dev.persefonia.app.contentpublishing.persistence.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class ContentPublishingCustomSqlSpikeTest extends ContentPublishingJdbcSpikeSupport {
    @Test
    void customRouteQueryReturnsOnlyPubliclyEligiblePublishedRows() {
        SpikeContentItem publicPublished = publishedItem("public-published");
        SpikeContentItem unlistedPublished = publishedItem("unlisted-published")
                .withVisibility(SpikeContentVisibility.UNLISTED);
        SpikeContentItem draft = publishedItem("draft-route").withStatus(SpikeContentStatus.DRAFT);
        SpikeContentItem unpublished = publishedItem("unpublished-route").withStatus(SpikeContentStatus.UNPUBLISHED);
        SpikeContentItem archived = publishedItem("archived-route").withStatus(SpikeContentStatus.ARCHIVED);
        SpikeContentItem privatePublished = publishedItem("private-route").withVisibility(SpikeContentVisibility.PRIVATE);

        List.of(publicPublished, unlistedPublished, draft, unpublished, archived, privatePublished)
                .forEach(adapter::saveContentItem);

        assertThat(adapter.findEligibleRouteIds(SpikeContentType.ARTICLE, SpikeLanguage.EN, NOW))
                .containsExactlyInAnyOrder(publicPublished.id(), unlistedPublished.id());
    }

    @Test
    void rejectsInvalidContentStatus() {
        assertThatThrownBy(() -> insertRawContentItem(Map.of("status", "BROKEN")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsInvalidVisibility() {
        assertThatThrownBy(() -> insertRawContentItem(Map.of("visibility", "HIDDEN")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsInvalidContentType() {
        assertThatThrownBy(() -> insertRawContentItem(Map.of("type", "BOOK")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsInvalidLanguage() {
        assertThatThrownBy(() -> insertRawContentItem(Map.of("language", "FR")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsInvalidSlug() {
        assertThatThrownBy(() -> insertRawContentItem(Map.of("slug", "Invalid Slug!")))
                .isInstanceOf(DataAccessException.class);
    }
}
