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
    }
}
