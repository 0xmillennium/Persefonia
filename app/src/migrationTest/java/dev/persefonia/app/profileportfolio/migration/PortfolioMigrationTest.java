package dev.persefonia.app.profileportfolio.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PortfolioMigrationTest {
    @BeforeAll
    static void migrateDatabase() {
        PortfolioMigrationDatabase.start();
    }

    @Test
    void cleanMigrateCreatesPortfolioCoreTables() throws SQLException {
        assertThatCode(PortfolioMigrationDatabase::cleanMigrate).doesNotThrowAnyException();

        assertThat(PortfolioSql.strings("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'portfolio'
                  AND table_name IN ('site_presentation_settings', 'personal_profiles', 'projects')
                """)).containsExactlyInAnyOrder("site_presentation_settings", "personal_profiles", "projects");
    }
}
