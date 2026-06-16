package dev.persefonia.app.profileportfolio.persistence;

import dev.persefonia.profileportfolio.domain.profile.PersonalProfileRepository;
import dev.persefonia.profileportfolio.domain.project.ProjectRepository;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
abstract class PortfolioRepositoryTestDatabase {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    private static boolean migrated;

    @Autowired SitePresentationSettingsRepository settings;
    @Autowired PersonalProfileRepository profiles;
    @Autowired ProjectRepository projects;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void resetPortfolioTables() {
        migrateOnce();
        jdbc.execute("""
                TRUNCATE portfolio.project_tags,
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
                    portfolio.personal_profiles,
                    portfolio.site_supported_languages,
                    portfolio.site_presentation_settings
                CASCADE
                """);
        jdbc.execute("""
                INSERT INTO portfolio.site_presentation_settings (
                    id, singleton_key, site_name, default_language, default_theme,
                    show_featured_projects, show_latest_writing, show_research_highlights,
                    featured_project_limit, latest_writing_limit, updated_at, version
                ) VALUES (
                    '00000000-0000-0000-0000-000000000701', true, 'Persefonia', 'TR', 'SYSTEM',
                    true, true, false, 3, 5, timestamp with time zone '2026-06-16T10:00:00Z', 0
                )
                """);
        jdbc.execute("""
                INSERT INTO portfolio.site_supported_languages (settings_id, language)
                VALUES
                    ('00000000-0000-0000-0000-000000000701', 'TR'),
                    ('00000000-0000-0000-0000-000000000701', 'EN')
                """);
    }

    private static synchronized void migrateOnce() {
        if (migrated) {
            return;
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas("operations")
                .createSchemas(true)
                .load()
                .migrate();
        migrated = true;
    }
}
