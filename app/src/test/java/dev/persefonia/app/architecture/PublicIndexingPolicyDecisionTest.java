package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicIndexingPolicyDecisionTest {
    private static final Path DECISION_DIR = Path.of("../docs/decisions");
    private static final Path PUBLIC_INDEX_DECISION =
            DECISION_DIR.resolve("0013-use-discovery-eligibility-for-public-index-surfaces.md");
    private static final Path SEARCH_DECISION =
            DECISION_DIR.resolve("0014-use-postgresql-full-text-search-for-public-search.md");
    private static final Path PUBLIC_DOCUMENTS_DECISION =
            DECISION_DIR.resolve("0015-publish-machine-readable-public-discovery-documents.md");
    private static final Path OLD_OMNIBUS_DECISION =
            DECISION_DIR.resolve("0013-define-public-search-sitemap-feed-and-robots-policy.md");

    @Test
    void publicIndexingDecisionRecordsExistUseSequentialNumbersAndAreIndexed() throws Exception {
        assertThat(PUBLIC_INDEX_DECISION).exists().isRegularFile();
        assertThat(SEARCH_DECISION).exists().isRegularFile();
        assertThat(PUBLIC_DOCUMENTS_DECISION).exists().isRegularFile();
        assertThat(OLD_OMNIBUS_DECISION).doesNotExist();

        List<String> decisionFiles;
        try (var paths = Files.list(DECISION_DIR)) {
            decisionFiles = paths
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("\\d{4}-.*\\.md"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }

        assertThat(decisionFiles)
                .extracting(name -> Integer.parseInt(name.substring(0, 4)))
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, decisionFiles.size())
                        .boxed()
                        .toList());

        assertThat(Files.readString(DECISION_DIR.resolve("INDEX.md")))
                .contains("0013-use-discovery-eligibility-for-public-index-surfaces.md")
                .contains("0014-use-postgresql-full-text-search-for-public-search.md")
                .contains("0015-publish-machine-readable-public-discovery-documents.md")
                .doesNotContain("0013-define-public-search-sitemap-feed-and-robots-policy.md");
    }

    @Test
    void publicIndexDecisionLocksDiscoveryEligibilityAndResourceExposurePolicy() throws Exception {
        String decision = Files.readString(PUBLIC_INDEX_DECISION);

        assertThat(decision)
                .contains("Dynamic public search, sitemap, and feed entries use the Discovery current projection")
                .contains("discovery.discoverable_resources")
                .contains("indexing_policy")
                .contains("search_eligibility")
                .contains("sitemap_eligibility")
                .contains("feed_eligibility")
                .contains("UNLISTED` resources remain direct URL only")
                .contains("PRIVATE`, draft, unpublished, and archived resources are excluded")
                .contains("Listed public project detail pages may become search and sitemap eligible")
                .contains("Projects remain feed ineligible")
                .contains("Tag and series pages remain `NO_INDEX`")
                .contains("Media image variants and CV downloads are excluded")
                .contains("Generic Media PDF/original/download routes remain forbidden")
                .doesNotContain("## Public resource eligibility matrix")
                .doesNotContain("## Out of scope")
                .doesNotContain("## Enforcement / tests");
    }

    @Test
    void searchDecisionLocksTechnologyAndPrivacyPolicy() throws Exception {
        String decision = Files.readString(SEARCH_DECISION);

        assertThat(decision)
                .contains("Public search uses PostgreSQL full text search")
                .contains("discovery.discoverable_resources.search_text")
                .contains("does not add a `searchVector`")
                .contains("Elasticsearch and OpenSearch are out of scope")
                .contains("Search result pages are `noindex, follow`")
                .contains("Search terms are not persisted")
                .contains("Search terms are not written to Insights")
                .contains("must not intentionally log raw search query terms")
                .doesNotContain("## Public resource eligibility matrix")
                .doesNotContain("## Out of scope")
                .doesNotContain("## Enforcement / tests");
    }

    @Test
    void publicDocumentsDecisionLocksSitemapRobotsFeedMetadataAndCachePolicy() throws Exception {
        String decision = Files.readString(PUBLIC_DOCUMENTS_DECISION);

        assertThat(decision)
                .contains("Sitemap XML uses absolute URLs")
                .contains("Static sitemap entries come from a small explicit allowlist")
                .contains("CV page only when an active CV exists")
                .contains("Sitemap excludes search pages")
                .contains("CV download URLs")
                .contains("media binary URLs")
                .contains("robots.txt` is advisory and not security")
                .contains("The first feed format is Atom 1.0")
                .contains("projects, tags, series, CV, and media binaries are feed ineligible")
                .contains("must not invent fake OpenGraph images")
                .contains("default project cover placeholders")
                .contains("generic Media URLs for OpenGraph metadata")
                .contains("Sitemap, robots, and feed responses use explicit public cache")
                .doesNotContain("## Public resource eligibility matrix")
                .doesNotContain("## Out of scope")
                .doesNotContain("## Enforcement / tests");
    }

    @Test
    void decisionPreservesAndExtendsExistingPublicRouteDecisions() throws Exception {
        String publicIndexDecision = Files.readString(PUBLIC_INDEX_DECISION);
        String tagSeries = Files.readString(
                DECISION_DIR.resolve("0011-reserve-tag-and-series-public-route-projections.md"));
        String navigation = Files.readString(
                DECISION_DIR.resolve("0012-constrain-public-navigation-and-hreflang-to-listed-public-content.md"));

        assertThat(tagSeries)
                .contains("Tag and series page projections are initially not eligible for search, feed, or sitemap");
        assertThat(navigation)
                .contains("UNLISTED` content remains direct URL only")
                .contains("Public tag pages and public series pages are public read surfaces, but they remain `NO_INDEX`");

        assertThat(publicIndexDecision)
                .contains("Tag and series pages remain `NO_INDEX`")
                .contains("UNLISTED` resources remain direct URL only");
    }
}
