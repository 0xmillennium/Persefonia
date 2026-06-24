package dev.persefonia.webpublic.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.index.PublicSearchIndexQueryService;
import dev.persefonia.discovery.application.index.PublicSearchRequest;
import dev.persefonia.discovery.application.index.PublicSearchResult;
import dev.persefonia.discovery.application.index.PublicSearchResultPage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicSearchPageServiceTest {
    private final TrackingSearchIndex searchIndex = new TrackingSearchIndex();
    private final PublicSearchPageService pages = new PublicSearchPageService(searchIndex);

    @Test
    void blankQueryBuildsEmptyFormModelWithoutCallingSearch() {
        PublicSearchPage page = pages.page("   ", null, "https://example.test/search", List.of("/assets/main.css"));

        assertThat(page.hasQuery()).isFalse();
        assertThat(page.hasValidationError()).isFalse();
        assertThat(page.results()).isEmpty();
        assertThat(page.canonicalUrl()).isEqualTo("https://example.test/search");
        assertThat(page.stylesheetPaths()).containsExactly("/assets/main.css");
        assertThat(searchIndex.calls()).isZero();
    }

    @Test
    void invalidQueryBuildsValidationModelWithoutCallingSearch() {
        PublicSearchPage page = pages.page("a", null, "https://example.test/search", List.of());

        assertThat(page.hasQuery()).isTrue();
        assertThat(page.hasValidationError()).isTrue();
        assertThat(page.validationError()).isEqualTo("Enter at least 2 characters to search.");
        assertThat(page.results()).isEmpty();
        assertThat(searchIndex.calls()).isZero();
    }

    @Test
    void longAndControlCharacterQueriesAreRejectedBeforeSearch() {
        assertThat(pages.page("x".repeat(121), null, "https://example.test/search", List.of()).validationError())
                .isEqualTo("Search query is too long.");
        assertThat(pages.page("hello\u0000world", null, "https://example.test/search", List.of()).validationError())
                .isEqualTo("Search query contains unsupported characters.");
        assertThat(searchIndex.calls()).isZero();
    }

    @Test
    void invalidPageBuildsValidationModelWithoutCallingSearch() {
        PublicSearchPage page = pages.page("portfolio", "invalid", "https://example.test/search", List.of());

        assertThat(page.hasValidationError()).isTrue();
        assertThat(page.validationError()).isEqualTo("Page number is invalid.");
        assertThat(searchIndex.calls()).isZero();
    }

    @Test
    void validQueryCallsSearchAndMapsResults() {
        searchIndex.nextPage = new PublicSearchResultPage(
                "public index",
                20,
                20,
                22L,
                List.of(result("CONTENT_ITEM", DiscoveryLanguage.EN, "/en/articles/public-index")));

        PublicSearchPage page = pages.page("  public\t index  ", "2", "https://example.test/search", List.of());

        assertThat(searchIndex.calls()).isEqualTo(1);
        assertThat(searchIndex.lastRequest().query()).isEqualTo("public index");
        assertThat(searchIndex.lastRequest().limit()).isEqualTo(20);
        assertThat(searchIndex.lastRequest().offset()).isEqualTo(20);
        assertThat(page.totalCount()).isEqualTo(22L);
        assertThat(page.currentPage()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.hasPreviousPage()).isTrue();
        assertThat(page.hasNextPage()).isFalse();
        assertThat(page.previousPageUrl()).isEqualTo("/search?q=public%20index&page=1");
        assertThat(page.results()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("Title <escaped>");
            assertThat(item.summary()).isEqualTo("Summary <escaped>");
            assertThat(item.publicUrl()).isEqualTo("/en/articles/public-index");
            assertThat(item.resourceType()).isEqualTo("Content");
            assertThat(item.languageLabel()).isEqualTo("English");
        });
    }

    @Test
    void mapsProjectAndTurkishLabelsAndNextPageUrl() {
        searchIndex.nextPage = new PublicSearchResultPage(
                "proje",
                20,
                0,
                21L,
                List.of(result("PROJECT", DiscoveryLanguage.TR, "/tr/projects/proje")));

        PublicSearchPage page = pages.page("proje", null, "https://example.test/search", List.of());

        assertThat(page.results()).singleElement().satisfies(item -> {
            assertThat(item.resourceType()).isEqualTo("Project");
            assertThat(item.languageLabel()).isEqualTo("Turkish");
        });
        assertThat(page.hasNextPage()).isTrue();
        assertThat(page.nextPageUrl()).isEqualTo("/search?q=proje&page=2");
    }

    @Test
    void rejectsUnsafePublicUrlsFromSearchResults() {
        searchIndex.nextPage = new PublicSearchResultPage(
                "public",
                20,
                0,
                1L,
                List.of(result("PROJECT", DiscoveryLanguage.EN, "https://example.test/project")));

        assertThatThrownBy(() -> pages.page("public", null, "https://example.test/search", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("publicUrl must be an application-relative path");
    }

    private static PublicSearchResult result(String sourceType, DiscoveryLanguage language, String publicUrl) {
        return new PublicSearchResult(
                sourceType,
                "source-id",
                language,
                publicUrl,
                "https://example.test" + publicUrl,
                "Title <escaped>",
                "Summary <escaped>",
                Instant.parse("2026-06-20T12:00:00Z"),
                Instant.parse("2026-06-21T12:00:00Z"),
                1.0d);
    }

    private static final class TrackingSearchIndex implements PublicSearchIndexQueryService {
        private PublicSearchRequest lastRequest;
        private PublicSearchResultPage nextPage = new PublicSearchResultPage("query", 20, 0, 0L, List.of());

        @Override
        public PublicSearchResultPage search(PublicSearchRequest request) {
            this.lastRequest = request;
            return nextPage;
        }

        int calls() {
            return lastRequest == null ? 0 : 1;
        }

        PublicSearchRequest lastRequest() {
            return lastRequest;
        }
    }
}
