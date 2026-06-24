package dev.persefonia.app.discovery.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.index.PublicFeedIndexQueryService;
import dev.persefonia.discovery.application.index.PublicSearchIndexQueryService;
import dev.persefonia.discovery.application.index.PublicSearchRequest;
import dev.persefonia.discovery.application.index.PublicSitemapIndexQueryService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PublicSearchPublicSitemapPublicFeedSeriesDiscoveryIndexAdapterTest extends DiscoveryRepositoryTestDatabase {
    private static final Instant PUBLISHED_NEW = Instant.parse("2026-06-21T10:00:00Z");
    private static final Instant PUBLISHED_OLD = Instant.parse("2026-06-20T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-06-22T10:00:00Z");

    @Autowired PublicSearchIndexQueryService search;
    @Autowired PublicSitemapIndexQueryService sitemap;
    @Autowired PublicFeedIndexQueryService feed;

    @Test
    void publicSearchReturnsOnlyIndexAndSearchEligibleMatchingResources() {
        seedPublicIndexRows();

        var page = search.search(new PublicSearchRequest("aurora", 20, 0));

        assertThat(page.normalizedQuery()).isEqualTo("aurora");
        assertThat(page.totalCount()).isEqualTo(3);
        assertThat(page.results())
                .extracting(result -> result.publicUrl())
                .containsExactly(
                        "/en/articles/aurora-article",
                        "/en/pages/aurora-page",
                        "/en/projects/aurora-project");
    }

    @Test
    void publicSearchEnforcesLimitOffsetAndUsesSameFiltersForCount() {
        seedPublicIndexRows();

        var page = search.search(new PublicSearchRequest("aurora", 1, 1));

        assertThat(page.totalCount()).isEqualTo(3);
        assertThat(page.results())
                .extracting(result -> result.publicUrl())
                .containsExactly("/en/pages/aurora-page");
    }

    @Test
    void publicSearchUsesParameterizedFullTextQueryForInjectionLikeInput() {
        seedPublicIndexRows();

        var page = search.search(new PublicSearchRequest("aurora'; --", 20, 0));

        assertThat(page.results())
                .extracting(result -> result.publicUrl())
                .containsExactly(
                        "/en/articles/aurora-article",
                        "/en/pages/aurora-page",
                        "/en/projects/aurora-project");
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('discovery.search_terms')",
                String.class)).isNull();
    }

    @Test
    void publicSitemapReturnsOnlyIndexAndSitemapEligibleDynamicResources() {
        seedPublicIndexRows();

        assertThat(sitemap.findSitemapEntries(20))
                .extracting(entry -> entry.publicUrl())
                .containsExactly(
                        "/en/articles/aurora-article",
                        "/en/notes/aurora-note",
                        "/en/pages/aurora-page",
                        "/en/projects/aurora-project");
    }

    @Test
    void publicSitemapEnforcesLimit() {
        seedPublicIndexRows();

        assertThat(sitemap.findSitemapEntries(2))
                .extracting(entry -> entry.publicUrl())
                .containsExactly("/en/articles/aurora-article", "/en/notes/aurora-note");
        assertThatThrownBy(() -> sitemap.findSitemapEntries(50_001))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicFeedReturnsOnlyIndexAndFeedEligiblePublishedResources() {
        seedPublicIndexRows();

        assertThat(feed.findLatestFeedEntries(20))
                .extracting(entry -> entry.publicUrl())
                .containsExactly(
                        "/en/articles/aurora-article",
                        "/en/notes/aurora-note");
    }

    @Test
    void publicFeedEnforcesLimit() {
        seedPublicIndexRows();

        assertThat(feed.findLatestFeedEntries(1))
                .extracting(entry -> entry.publicUrl())
                .containsExactly("/en/articles/aurora-article");
        assertThatThrownBy(() -> feed.findLatestFeedEntries(51))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void seedPublicIndexRows() {
        insertResource("CONTENT_PUBLISHING", "CONTENT_ITEM", "ARTICLE", "DETAIL",
                "/en/articles/aurora-article", "INDEX", "ELIGIBLE", "ELIGIBLE", "ELIGIBLE",
                PUBLISHED_NEW, UPDATED, "aurora public discovery");
        insertResource("CONTENT_PUBLISHING", "CONTENT_ITEM", "PAGE", "DETAIL",
                "/en/pages/aurora-page", "INDEX", "ELIGIBLE", "ELIGIBLE", "NOT_ELIGIBLE",
                PUBLISHED_OLD, PUBLISHED_OLD, "aurora public discovery");
        insertResource("CONTENT_PUBLISHING", "CONTENT_ITEM", "NOTE", "DETAIL",
                "/en/notes/aurora-note", "INDEX", "ELIGIBLE", "ELIGIBLE", "ELIGIBLE",
                PUBLISHED_OLD, UPDATED, "notes only feed");
        insertResource("PROFILE_PORTFOLIO", "PROJECT", "PROJECT", "DETAIL",
                "/en/projects/aurora-project", "INDEX", "ELIGIBLE", "ELIGIBLE", "NOT_ELIGIBLE",
                null, UPDATED, "aurora public discovery");
        insertResource("CONTENT_PUBLISHING", "CONTENT_ITEM", "ARTICLE", "DETAIL",
                "/en/articles/no-index", "NO_INDEX", "ELIGIBLE", "ELIGIBLE", "ELIGIBLE",
                PUBLISHED_NEW, UPDATED, "aurora public discovery");
        insertResource("CONTENT_PUBLISHING", "CONTENT_ITEM", "ARTICLE", "DETAIL",
                "/en/articles/not-eligible", "INDEX", "NOT_ELIGIBLE", "NOT_ELIGIBLE", "NOT_ELIGIBLE",
                PUBLISHED_NEW, UPDATED, "aurora public discovery");
        insertResource("CONTENT_PUBLISHING", "CONTENT_ITEM", "ARTICLE", "DETAIL",
                "/en/articles/unlisted-direct", "NO_INDEX", "NOT_ELIGIBLE", "NOT_ELIGIBLE", "NOT_ELIGIBLE",
                PUBLISHED_NEW, UPDATED, "aurora public discovery");
        insertResource("TAXONOMY", "TAG", "TAG", "TAG_PAGE",
                "/en/tags/aurora", "NO_INDEX", "NOT_ELIGIBLE", "NOT_ELIGIBLE", "NOT_ELIGIBLE",
                null, UPDATED, "aurora public discovery");
        insertResource("CONTENT_PUBLISHING", "SERIES", "SERIES", "SERIES_PAGE",
                "/en/series/aurora", "NO_INDEX", "NOT_ELIGIBLE", "NOT_ELIGIBLE", "NOT_ELIGIBLE",
                null, UPDATED, "aurora public discovery");
        insertResource("CONTENT_PUBLISHING", "CONTENT_ITEM", "ARTICLE", "DETAIL",
                "/en/articles/no-match", "INDEX", "ELIGIBLE", "NOT_ELIGIBLE", "NOT_ELIGIBLE",
                PUBLISHED_NEW, UPDATED, "completely different text");
    }

    private void insertResource(
            String sourceContext,
            String sourceType,
            String resourceType,
            String routePurpose,
            String publicUrl,
            String indexingPolicy,
            String searchEligibility,
            String sitemapEligibility,
            String feedEligibility,
            Instant publishedAt,
            Instant sourceUpdatedAt,
            String searchText) {
        UUID sourceEntityId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO discovery.discoverable_resources (
                    id, source_context, source_type, source_entity_id, resource_type, route_purpose, language,
                    public_url, canonical_url, title, summary, indexing_policy, search_eligibility,
                    sitemap_eligibility, feed_eligibility, published_at, source_updated_at, search_text,
                    created_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'EN', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                UUID.randomUUID(),
                sourceContext,
                sourceType,
                sourceEntityId,
                resourceType,
                routePurpose,
                publicUrl,
                "https://example.test" + publicUrl,
                "Title " + publicUrl,
                "Summary " + publicUrl,
                indexingPolicy,
                searchEligibility,
                sitemapEligibility,
                feedEligibility,
                timestamp(publishedAt),
                timestamp(sourceUpdatedAt),
                searchText,
                Timestamp.from(Instant.parse("2026-06-19T10:00:00Z")));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
