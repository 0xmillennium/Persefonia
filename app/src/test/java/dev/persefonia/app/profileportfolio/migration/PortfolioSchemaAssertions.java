package dev.persefonia.app.profileportfolio.migration;

import java.sql.SQLException;

final class PortfolioSchemaAssertions {
    private PortfolioSchemaAssertions() {
    }

    static long foreignKeyCount(String childTable, String childColumn, String parentSchema, String parentTable)
            throws SQLException {
        return PortfolioSql.count("""
                SELECT count(*)
                FROM pg_constraint constraint_catalog
                JOIN pg_class child_table ON child_table.oid = constraint_catalog.conrelid
                JOIN pg_namespace child_schema ON child_schema.oid = child_table.relnamespace
                JOIN pg_class parent_table ON parent_table.oid = constraint_catalog.confrelid
                JOIN pg_namespace parent_schema ON parent_schema.oid = parent_table.relnamespace
                JOIN unnest(constraint_catalog.conkey) WITH ORDINALITY child_key(attnum, ord) ON true
                JOIN pg_attribute child_attribute
                  ON child_attribute.attrelid = child_table.oid
                 AND child_attribute.attnum = child_key.attnum
                WHERE constraint_catalog.contype = 'f'
                  AND child_schema.nspname = 'portfolio'
                  AND child_table.relname = ?
                  AND child_attribute.attname = ?
                  AND parent_schema.nspname = ?
                  AND parent_table.relname = ?
                """, childTable, childColumn, parentSchema, parentTable);
    }

    static long portfolioForeignKeyCountToSchema(String parentSchemaName) throws SQLException {
        return PortfolioSql.count("""
                SELECT count(*)
                FROM pg_constraint constraint_catalog
                JOIN pg_class child_table ON child_table.oid = constraint_catalog.conrelid
                JOIN pg_namespace child_schema ON child_schema.oid = child_table.relnamespace
                JOIN pg_class parent_table ON parent_table.oid = constraint_catalog.confrelid
                JOIN pg_namespace parent_schema ON parent_schema.oid = parent_table.relnamespace
                WHERE constraint_catalog.contype = 'f'
                  AND child_schema.nspname = 'portfolio'
                  AND parent_schema.nspname = ?
                """, parentSchemaName);
    }
}
