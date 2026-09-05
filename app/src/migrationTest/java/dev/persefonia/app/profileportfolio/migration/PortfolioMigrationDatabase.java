package dev.persefonia.app.profileportfolio.migration;

import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

final class PortfolioMigrationDatabase {
    static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.migrationDatabase();

    private PortfolioMigrationDatabase() {
    }

    static void start() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
    }

    static MigrateResult cleanMigrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas(
                        "operations",
                        "iam",
                        "taxonomy",
                        "publishing",
                        "portfolio",
                        "media",
                        "communication",
                        "discovery",
                        "integrity",
                        "insights",
                        "audit",
                        "portability")
                .createSchemas(true)
                .cleanDisabled(false)
                .load();
        flyway.clean();
        return flyway.migrate();
    }

    static void truncatePortfolioMutableTables() throws SQLException {
        PortfolioSql.execute("""
                TRUNCATE portfolio.project_tags,
                    portfolio.active_cv_documents,
                    portfolio.project_links,
                    portfolio.project_technologies,
                    portfolio.project_case_study_sections,
                    portfolio.project_localizations,
                    portfolio.projects,
                    portfolio.current_focus_items,
                    portfolio.education_summaries,
                    portfolio.technical_focus_areas,
                    portfolio.external_profile_links,
                    portfolio.profile_localizations,
                    portfolio.personal_profiles
                CASCADE
                """);
    }
}
