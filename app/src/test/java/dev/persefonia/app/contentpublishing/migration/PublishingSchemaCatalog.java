package dev.persefonia.app.contentpublishing.migration;

import java.sql.SQLException;
import java.util.List;

final class PublishingSchemaCatalog {
    private PublishingSchemaCatalog() {
    }

    static List<String> publishingTablesNamed(String tableNamesSqlList) throws SQLException {
        return PublishingSql.strings("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'publishing'
                  AND table_name IN (%s)
                """.formatted(tableNamesSqlList));
    }

    static List<String> publishingIndexNames() throws SQLException {
        return PublishingSql.strings("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'publishing'
                """);
    }

    static List<String> publishingConstraintAndIndexNames() throws SQLException {
        return PublishingSql.strings("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'publishing'
                UNION
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'publishing'
                """);
    }

    static long foreignKeyCountFromPublishingTo(String parentSchemasSqlList) throws SQLException {
        return PublishingSql.count("""
                SELECT count(*)
                FROM pg_constraint constraint_catalog
                JOIN pg_class child_table
                  ON child_table.oid = constraint_catalog.conrelid
                JOIN pg_namespace child_schema
                  ON child_schema.oid = child_table.relnamespace
                JOIN pg_class parent_table
                  ON parent_table.oid = constraint_catalog.confrelid
                JOIN pg_namespace parent_schema
                  ON parent_schema.oid = parent_table.relnamespace
                WHERE constraint_catalog.contype = 'f'
                  AND child_schema.nspname = 'publishing'
                  AND parent_schema.nspname IN (%s)
                """.formatted(parentSchemasSqlList));
    }

    static long publishingColumnCountMatching(String predicateSql) throws SQLException {
        return PublishingSql.count("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'publishing'
                  AND (%s)
                """.formatted(predicateSql));
    }
}
