package dev.persefonia.app.profileportfolio.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.profile.ProfileId;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Aggregate hydration must work without borrowing additional connections for child rows. */
class PortfolioAggregateConnectionUsageTest {
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.integrationDatabase();
    private HikariDataSource pool;
    private JdbcPersonalProfileRepositoryAdapter profiles;
    private JdbcProjectRepositoryAdapter projects;
    private JdbcActiveCvProfileRepositoryAdapter cv;
    private JdbcSitePresentationSettingsRepositoryAdapter settings;
    private JdbcProjectPublicReadModelAdapter publicProjects;
    private JdbcProjectAdminReadModelAdapter adminProjects;
    private ProfileId profileId;
    private ProjectId projectId;

    @BeforeEach
    void setUpSingleConnectionPoolAndOwnedGraphs() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(2000);
        pool = new HikariDataSource(config);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("jdbc", new NamedParameterJdbcTemplate(pool));
        beans.addBean("transactions", new TransactionTemplate(new JdbcTransactionManager(pool)));
        var jdbcProvider = beans.getBeanProvider(NamedParameterJdbcTemplate.class);
        var transactions = beans.getBeanProvider(TransactionTemplate.class);
        profiles = new JdbcPersonalProfileRepositoryAdapter(jdbcProvider, transactions);
        projects = new JdbcProjectRepositoryAdapter(jdbcProvider, transactions);
        cv = new JdbcActiveCvProfileRepositoryAdapter(jdbcProvider, transactions);
        settings = new JdbcSitePresentationSettingsRepositoryAdapter(jdbcProvider, transactions);
        publicProjects = new JdbcProjectPublicReadModelAdapter(jdbcProvider);
        adminProjects = new JdbcProjectAdminReadModelAdapter(jdbcProvider);
        JdbcTemplate jdbc = new JdbcTemplate(pool);
        profileId = ProfileId.newId();
        projectId = ProjectId.newId();
        UUID localization = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO portfolio.personal_profiles
                    (id, display_name, active, created_at, updated_at, version)
                VALUES (?, 'Connection regression', true, now(), now(), 0)
                """, profileId.value());
        jdbc.update("""
                INSERT INTO portfolio.profile_localizations
                    (id, profile_id, language, short_bio, long_bio)
                VALUES (?, ?, 'EN', 'Short', 'Long')
                """, localization, profileId.value());
        jdbc.update("""
                INSERT INTO portfolio.current_focus_items (id, profile_localization_id, text, sort_order)
                VALUES (?, ?, 'Concurrency', 1)
                """, UUID.randomUUID(), localization);
        jdbc.update("""
                INSERT INTO portfolio.projects
                    (id, status, visibility, featured, sort_order, created_at, updated_at, version)
                VALUES (?, 'ACTIVE', 'PUBLIC', false, 1, now(), now(), 0)
                """, projectId.value());
        UUID projectLocalization = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO portfolio.project_localizations (id, project_id, language, slug, title, summary)
                VALUES (?, ?, 'EN', 'connection-regression', 'Project', 'Summary')
                """, projectLocalization, projectId.value());
        jdbc.update("""
                INSERT INTO portfolio.project_case_study_sections (id, project_localization_id, type, body, sort_order)
                VALUES (?, ?, 'PROBLEM', 'Connection ownership', 1)
                """, UUID.randomUUID(), projectLocalization);
        jdbc.update("""
                INSERT INTO portfolio.active_cv_documents
                    (id, active_cv_profile_id, language, asset_id, selected_at, created_at, updated_at)
                SELECT ?, id, 'EN', ?, now(), now(), now() FROM portfolio.active_cv_profiles
                """, UUID.randomUUID(), UUID.randomUUID());
    }

    @AfterEach
    void closePool() {
        if (pool != null) pool.close();
    }

    @Test
    void profileReadsUseOneConnectionIncludingNestedLocalizationChildren() {
        assertThat(profiles.findActiveProfile().orElseThrow().localizations().getFirst().currentFocusItems())
                .hasSize(1);
        assertThat(profiles.findById(profileId).orElseThrow().id()).isEqualTo(profileId);
        assertPoolReturned();
    }

    @Test
    void projectReadsUseOneConnectionIncludingCaseStudySections() {
        assertThat(projects.findById(projectId).orElseThrow().localizations().getFirst().sections()).hasSize(1);
        assertThat(projects.findBySlug(ProjectSlug.of("connection-regression"), ContentLanguage.EN)
                .orElseThrow().id()).isEqualTo(projectId);
        assertPoolReturned();
    }

    @Test
    void cvReadsUseOneConnectionIncludingSelectedDocuments() {
        var profile = cv.findSingleton().orElseThrow();
        assertThat(profile.documents()).hasSize(1);
        assertThat(cv.findById(profile.id()).orElseThrow().documents()).hasSize(1);
        assertPoolReturned();
    }

    @Test
    void settingsReadsUseOneConnectionIncludingSupportedLanguages() {
        var current = settings.findCurrent().orElseThrow();
        assertThat(current.supportedLanguages()).isNotEmpty();
        assertThat(settings.findById(current.id()).orElseThrow().supportedLanguages())
                .isEqualTo(current.supportedLanguages());
        assertPoolReturned();
    }

    @Test
    void publicProjectDetailReadsUseOneConnectionIncludingSections() {
        var detail = publicProjects.findDetail(projectId, ContentLanguage.EN,
                ProjectSlug.of("connection-regression")).orElseThrow();
        assertThat(detail.sections()).hasSize(1);
        assertPoolReturned();
    }

    @Test
    void adminProjectDetailReadsUseOneConnectionIncludingLocalizedSections() {
        var detail = adminProjects.findDetails(projectId).orElseThrow();
        assertThat(detail.localizations().getFirst().sections()).hasSize(1);
        assertPoolReturned();
    }

    @Test
    void concurrentAggregateReadsFinishAndReturnThePoolToIdle() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(4)) {
            ArrayList<Future<?>> results = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                results.add(workers.submit(() -> {
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    for (int iteration = 0; iteration < 8; iteration++) {
                        assertThat(profiles.findActiveProfile().orElseThrow().id()).isEqualTo(profileId);
                        assertThat(projects.findById(projectId).orElseThrow().id()).isEqualTo(projectId);
                        assertThat(cv.findSingleton().orElseThrow().documents()).hasSize(1);
                        assertThat(settings.findCurrent().orElseThrow().supportedLanguages()).isNotEmpty();
                        assertThat(publicProjects.findDetail(projectId, ContentLanguage.EN,
                                ProjectSlug.of("connection-regression")).orElseThrow().sections()).hasSize(1);
                        assertThat(adminProjects.findDetails(projectId).orElseThrow()
                                .localizations().getFirst().sections()).hasSize(1);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) result.get(15, TimeUnit.SECONDS);
        }
        assertPoolReturned();
    }

    private void assertPoolReturned() {
        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
        assertThat(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
        assertThat(new JdbcTemplate(pool).queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }
}
