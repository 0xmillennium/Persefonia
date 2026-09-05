package dev.persefonia.app.discovery.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiscoveryMigrationTest extends DiscoveryMigrationDatabase {
    @Test
    void discoveryAcceptsTagPageProjection() {
        insertResource(java.util.Map.of(
                "sourceContext", "TAXONOMY",
                "sourceType", "TAG",
                "resourceType", "TAG",
                "routePurpose", "TAG_PAGE",
                "publicUrl", "/en/tags/spring",
                "canonicalUrl", "https://example.test/en/tags/spring",
                "indexingPolicy", "NO_INDEX",
                "searchEligibility", "NOT_ELIGIBLE",
                "sitemapEligibility", "NOT_ELIGIBLE",
                "feedEligibility", "NOT_ELIGIBLE"));

        assertThat(jdbc().queryForObject("""
                SELECT count(*) FROM discovery.discoverable_resources
                WHERE source_context = 'TAXONOMY' AND source_type = 'TAG'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void discoveryRejectsInvalidTagProjectionValues() {
        assertThatThrownBy(() -> insertResource(java.util.Map.of(
                "sourceContext", "TAXONOMY",
                "sourceType", "TAG",
                "resourceType", "ARTICLE",
                "routePurpose", "TAG_PAGE")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertResource(java.util.Map.of(
                "sourceContext", "TAXONOMY",
                "sourceType", "TAG",
                "resourceType", "TAG",
                "routePurpose", "TAG_PAGE",
                "indexingPolicy", "INDEX")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void discoveryAcceptsSeriesPageProjection() {
        insertResource(java.util.Map.of(
                "sourceContext", "CONTENT_PUBLISHING",
                "sourceType", "SERIES",
                "resourceType", "SERIES",
                "routePurpose", "SERIES_PAGE",
                "publicUrl", "/en/series/spring-boot-notes",
                "canonicalUrl", "https://example.test/en/series/spring-boot-notes",
                "indexingPolicy", "NO_INDEX",
                "searchEligibility", "NOT_ELIGIBLE",
                "sitemapEligibility", "NOT_ELIGIBLE",
                "feedEligibility", "NOT_ELIGIBLE"));

        assertThat(jdbc().queryForObject("""
                SELECT count(*) FROM discovery.discoverable_resources
                WHERE source_context = 'CONTENT_PUBLISHING' AND source_type = 'SERIES'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void discoveryRejectsInvalidSeriesProjectionValues() {
        assertThatThrownBy(() -> insertResource(java.util.Map.of(
                "sourceContext", "CONTENT_PUBLISHING",
                "sourceType", "SERIES",
                "resourceType", "ARTICLE",
                "routePurpose", "SERIES_PAGE",
                "indexingPolicy", "NO_INDEX",
                "searchEligibility", "NOT_ELIGIBLE",
                "sitemapEligibility", "NOT_ELIGIBLE",
                "feedEligibility", "NOT_ELIGIBLE")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertResource(java.util.Map.of(
                "sourceContext", "CONTENT_PUBLISHING",
                "sourceType", "SERIES",
                "resourceType", "SERIES",
                "routePurpose", "SERIES_PAGE",
                "indexingPolicy", "INDEX",
                "searchEligibility", "NOT_ELIGIBLE",
                "sitemapEligibility", "NOT_ELIGIBLE",
                "feedEligibility", "NOT_ELIGIBLE")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void discoveryAcceptsProjectDetailProjection() {
        insertResource(java.util.Map.of(
                "sourceContext", "PROFILE_PORTFOLIO",
                "sourceType", "PROJECT",
                "resourceType", "PROJECT",
                "routePurpose", "DETAIL",
                "publicUrl", "/en/projects/persefonia",
                "canonicalUrl", "https://example.test/en/projects/persefonia",
                "indexingPolicy", "NO_INDEX",
                "searchEligibility", "NOT_ELIGIBLE",
                "sitemapEligibility", "NOT_ELIGIBLE",
                "feedEligibility", "NOT_ELIGIBLE"));

        assertThat(jdbc().queryForObject("""
                SELECT count(*) FROM discovery.discoverable_resources
                WHERE source_context = 'PROFILE_PORTFOLIO' AND source_type = 'PROJECT'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void discoveryAcceptsPublicProjectProjectionWithSearchAndSitemapEligibilityButNoFeedEligibility() {
        insertResource(java.util.Map.of(
                "sourceContext", "PROFILE_PORTFOLIO",
                "sourceType", "PROJECT",
                "resourceType", "PROJECT",
                "routePurpose", "DETAIL",
                "publicUrl", "/en/projects/public-discovery",
                "canonicalUrl", "https://example.test/en/projects/public-discovery",
                "indexingPolicy", "INDEX",
                "searchEligibility", "ELIGIBLE",
                "sitemapEligibility", "ELIGIBLE",
                "feedEligibility", "NOT_ELIGIBLE"));

        assertThat(jdbc().queryForObject("""
                SELECT count(*) FROM discovery.discoverable_resources
                WHERE source_context = 'PROFILE_PORTFOLIO'
                  AND source_type = 'PROJECT'
                  AND indexing_policy = 'INDEX'
                  AND search_eligibility = 'ELIGIBLE'
                  AND sitemap_eligibility = 'ELIGIBLE'
                  AND feed_eligibility = 'NOT_ELIGIBLE'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void discoveryRejectsProjectProjectionWithPublicFeedEligibility() {
        java.util.Map<String, Object> project = java.util.Map.of(
                "sourceContext", "PROFILE_PORTFOLIO",
                "sourceType", "PROJECT",
                "resourceType", "PROJECT",
                "routePurpose", "DETAIL",
                "publicUrl", "/en/projects/public-discovery",
                "canonicalUrl", "https://example.test/en/projects/public-discovery",
                "indexingPolicy", "INDEX",
                "searchEligibility", "ELIGIBLE",
                "sitemapEligibility", "ELIGIBLE",
                "feedEligibility", "NOT_ELIGIBLE");

        assertThatThrownBy(() -> insertResource(with(project, "feedEligibility", "ELIGIBLE")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void discoveryRejectsProjectProjectionWithWrongSourceTuple() {
        assertThatThrownBy(() -> insertResource(java.util.Map.of(
                "sourceContext", "PROFILE_PORTFOLIO",
                "sourceType", "CONTENT_ITEM",
                "resourceType", "PROJECT",
                "routePurpose", "DETAIL",
                "indexingPolicy", "NO_INDEX",
                "searchEligibility", "NOT_ELIGIBLE",
                "sitemapEligibility", "NOT_ELIGIBLE",
                "feedEligibility", "NOT_ELIGIBLE")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void redirectRulesWereNotExpandedForProjectSourceReferences() {
        assertThatThrownBy(() -> insertRedirect(java.util.Map.of(
                "sourceContext", "PROFILE_PORTFOLIO",
                "sourceType", "PROJECT",
                "sourceEntityId", java.util.UUID.randomUUID())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void publicUrlAndCanonicalUrlRemainUnique() {
        insertResource(java.util.Map.of(
                "publicUrl", "/en/articles/unique",
                "canonicalUrl", "https://example.test/en/articles/unique"));

        assertThatThrownBy(() -> insertResource(java.util.Map.of(
                "publicUrl", "/en/articles/unique",
                "canonicalUrl", "https://example.test/en/articles/another")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertResource(java.util.Map.of(
                "publicUrl", "/en/articles/another",
                "canonicalUrl", "https://example.test/en/articles/unique")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void sourceRefCanHaveMultipleLanguagesForSameTag() {
        java.util.UUID tagId = java.util.UUID.randomUUID();
        java.util.Map<String, Object> common = new java.util.HashMap<>();
        common.put("sourceContext", "TAXONOMY");
        common.put("sourceType", "TAG");
        common.put("sourceEntityId", tagId);
        common.put("resourceType", "TAG");
        common.put("routePurpose", "TAG_PAGE");
        common.put("indexingPolicy", "NO_INDEX");
        common.put("searchEligibility", "NOT_ELIGIBLE");
        common.put("sitemapEligibility", "NOT_ELIGIBLE");
        common.put("feedEligibility", "NOT_ELIGIBLE");
        var english = new java.util.HashMap<>(common);
        english.put("language", "EN");
        english.put("publicUrl", "/en/tags/spring");
        english.put("canonicalUrl", "https://example.test/en/tags/spring");
        var turkish = new java.util.HashMap<>(common);
        turkish.put("language", "TR");
        turkish.put("publicUrl", "/tr/tags/spring");
        turkish.put("canonicalUrl", "https://example.test/tr/tags/spring");

        insertResource(english);
        insertResource(turkish);

        assertThat(jdbc().queryForObject("""
                SELECT count(*) FROM discovery.discoverable_resources
                WHERE source_entity_id = ?
                """, Integer.class, tagId)).isEqualTo(2);
    }

    @Test
    void cleanMigrationCreatesDiscoveryTablesAndRequiredConstraintsAndIndexes() {
        cleanMigrate();

        List<String> tables = jdbc().queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'discovery'
                ORDER BY table_name
                """, String.class);
        List<String> names = jdbc().queryForList("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema = 'discovery'
                UNION
                SELECT indexname FROM pg_indexes WHERE schemaname = 'discovery'
                """, String.class);

        assertThat(tables).contains("discoverable_resources", "redirect_rules");
        assertThat(names).contains(
                "pk_discoverable_resources",
                "uq_discoverable_resources_key",
                "uq_discoverable_resources_public_url",
                "uq_discoverable_resources_canonical_url",
                "ix_discoverable_resources_source_ref",
                "ix_discoverable_resources_search_fts",
                "ix_discoverable_resources_public_sitemap",
                "ix_discoverable_resources_public_feed",
                "pk_redirect_rules",
                "uq_redirect_rules_active_source_url",
                "ck_redirect_rules_source_ref_all_or_none");
    }

    @Test
    void discoverableResourcesHasNoForbiddenCurrentProjectionColumnsAndNoForeignKeys() {
        List<String> columns = jdbc().queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'discovery' AND table_name = 'discoverable_resources'
                """, String.class);
        Integer foreignKeys = jdbc().queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_schema = 'discovery' AND constraint_type = 'FOREIGN KEY'
                """, Integer.class);

        assertThat(columns).doesNotContain(
                "active", "deleted_at", "valid_from", "valid_to", "history_id", "revision_id",
                "search_vector", "metadata_json", "payload_json", "source_payload");
        assertThat(columns).contains("id", "public_url", "canonical_url", "search_text");
        assertThat(foreignKeys).isZero();
        assertThat(jdbc().queryForObject(
                "SELECT to_regclass('discovery.discoverable_resource_history')",
                String.class)).isNull();
        assertThat(jdbc().queryForObject(
                "SELECT to_regclass('discovery.search_terms')",
                String.class)).isNull();
    }

    @Test
    void publicIndexMigrationCreatesExpectedPartialIndexesOnlyOnDiscoveryProjection() {
        List<String> indexes = jdbc().queryForList("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'discovery'
                  AND tablename = 'discoverable_resources'
                ORDER BY indexname
                """, String.class);

        assertThat(indexes).anySatisfy(index -> assertThat(index)
                .contains("ix_discoverable_resources_search_fts")
                .contains("USING gin")
                .contains("to_tsvector('simple'::regconfig, COALESCE(search_text, ''::text))")
                .contains("indexing_policy = 'INDEX'::text")
                .contains("search_eligibility = 'ELIGIBLE'::text"));
        assertThat(indexes).anySatisfy(index -> assertThat(index)
                .contains("ix_discoverable_resources_public_sitemap")
                .contains("language")
                .contains("source_updated_at DESC")
                .contains("public_url")
                .contains("sitemap_eligibility = 'ELIGIBLE'::text"));
        assertThat(indexes).anySatisfy(index -> assertThat(index)
                .contains("ix_discoverable_resources_public_feed")
                .contains("published_at DESC")
                .contains("source_updated_at DESC")
                .contains("public_url")
                .contains("feed_eligibility = 'ELIGIBLE'::text"));
    }

    @Test
    void discoverableResourcesStillHaveNoActiveFlag() {
        List<String> columns = jdbc().queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'discovery' AND table_name = 'discoverable_resources'
                """, String.class);

        assertThat(columns).doesNotContain("active");
    }

    @Test
    void discoveryStillHasNoHistoryTable() {
        assertThat(jdbc().queryForObject(
                "SELECT to_regclass('discovery.discoverable_resource_history')",
                String.class)).isNull();
    }

    private static java.util.Map<String, Object> with(
            java.util.Map<String, Object> original,
            String key,
            Object value) {
        java.util.Map<String, Object> copy = new java.util.HashMap<>(original);
        copy.put(key, value);
        copy.put("publicUrl", original.get("publicUrl") + "-" + key);
        copy.put("canonicalUrl", original.get("canonicalUrl") + "-" + key);
        return copy;
    }
}
