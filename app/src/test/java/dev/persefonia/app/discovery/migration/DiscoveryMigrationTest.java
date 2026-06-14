package dev.persefonia.app.discovery.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiscoveryMigrationTest extends DiscoveryMigrationDatabase {
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
    }
}
