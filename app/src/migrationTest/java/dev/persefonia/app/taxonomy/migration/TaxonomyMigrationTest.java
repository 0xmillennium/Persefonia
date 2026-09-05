package dev.persefonia.app.taxonomy.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

class TaxonomyMigrationTest {
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.migrationDatabase();
    private static JdbcTemplate jdbc;
    private static NamedParameterJdbcTemplate named;

    @BeforeAll
    static void migrate() {
        POSTGRES.start();
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        named = new NamedParameterJdbcTemplate(dataSource);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas("operations")
                .createSchemas(true)
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @Test
    void migrationCreatesTagsWithRequiredConstraintsIndexesAndNoForeignKeys() {
        List<String> names = jdbc.queryForList("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema = 'taxonomy' AND table_name = 'tags'
                UNION
                SELECT indexname FROM pg_indexes WHERE schemaname = 'taxonomy' AND tablename = 'tags'
                """, String.class);
        Integer foreignKeys = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_schema = 'taxonomy' AND table_name = 'tags' AND constraint_type = 'FOREIGN KEY'
                """, Integer.class);

        assertThat(names).contains(
                "pk_tags", "uq_tags_normalized_name", "uq_tags_slug",
                "ix_tags_status", "ix_tags_created_at", "ix_tags_updated_at");
        assertThat(foreignKeys).isZero();
    }

    @Test
    void databaseRejectsInvalidSlugAndStatus() {
        assertThatThrownBy(() -> insert("invalid slug", "ACTIVE")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("valid-slug", "UNKNOWN")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void insert(String slug, String status) {
        Instant now = Instant.parse("2026-06-15T10:00:00Z");
        named.update("""
                INSERT INTO taxonomy.tags (
                    id, name, normalized_name, slug, description, status, created_at, updated_at, version
                ) VALUES (
                    :id, :name, :normalizedName, :slug, NULL, :status, :createdAt, :updatedAt, 0
                )
                """, Map.of(
                        "id", UUID.randomUUID(),
                        "name", "Name " + UUID.randomUUID(),
                        "normalizedName", "name " + UUID.randomUUID(),
                        "slug", slug,
                        "status", status,
                        "createdAt", Timestamp.from(now),
                        "updatedAt", Timestamp.from(now)));
    }
}
