package dev.persefonia.discovery.application.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicIndexContractTest {
    @Test
    void validSearchRequestNormalizesQueryAndPreservesPaging() {
        PublicSearchRequest request = new PublicSearchRequest("  discovery\t  public\nindex  ", 20, 10);

        assertThat(request.query()).isEqualTo("discovery public index");
        assertThat(request.limit()).isEqualTo(20);
        assertThat(request.offset()).isEqualTo(10);
    }

    @Test
    void searchRequestRejectsBlankShortLongControlCharacterAndInvalidPaging() {
        assertThatThrownBy(() -> new PublicSearchRequest(" ", 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicSearchRequest("x", 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicSearchRequest("x".repeat(121), 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicSearchRequest("hello\u0000world", 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicSearchRequest("hello", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicSearchRequest("hello", 21, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicSearchRequest("hello", 10, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicSearchRequest("hello", 10, 10_001))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sitemapAndFeedLimitsAreBounded() {
        assertThat(PublicIndexLimits.requireSitemapLimit(50_000)).isEqualTo(50_000);
        assertThat(PublicIndexLimits.requireFeedLimit(50)).isEqualTo(50);

        assertThatThrownBy(() -> PublicIndexLimits.requireSitemapLimit(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicIndexLimits.requireSitemapLimit(50_001))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicIndexLimits.requireFeedLimit(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicIndexLimits.requireFeedLimit(51))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultRecordsRejectNullRequiredFields() {
        Instant now = Instant.parse("2026-06-24T10:00:00Z");
        PublicSearchResult result = new PublicSearchResult(
                "CONTENT_ITEM",
                "d4c57198-c3d4-477f-839b-7b48848628ec",
                DiscoveryLanguage.EN,
                "/en/articles/public-index",
                "https://example.test/en/articles/public-index",
                "Public Index",
                "Summary",
                now,
                now,
                0.5d);

        PublicSearchResultPage page = new PublicSearchResultPage("public index", 10, 0, 1, List.of(result));

        assertThat(page.results()).containsExactly(result);
        assertThatThrownBy(() -> new PublicSearchResult(
                null,
                "d4c57198-c3d4-477f-839b-7b48848628ec",
                DiscoveryLanguage.EN,
                "/en/articles/public-index",
                "https://example.test/en/articles/public-index",
                "Public Index",
                "Summary",
                now,
                now,
                0.5d)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PublicSitemapEntry(
                "/en/articles/public-index",
                "https://example.test/en/articles/public-index",
                DiscoveryLanguage.EN,
                null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PublicFeedEntry(
                "CONTENT_ITEM",
                "d4c57198-c3d4-477f-839b-7b48848628ec",
                DiscoveryLanguage.EN,
                "/en/articles/public-index",
                "https://example.test/en/articles/public-index",
                "Public Index",
                "Summary",
                null,
                now)).isInstanceOf(NullPointerException.class);
    }
}
