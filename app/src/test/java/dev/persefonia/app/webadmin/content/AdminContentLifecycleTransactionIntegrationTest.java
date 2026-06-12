package dev.persefonia.app.webadmin.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.contentpublishing.persistence.JdbcContentRevisionRepositoryAdapter;
import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.revision.port.ContentRevisionRepository;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import java.util.List;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"management.server.port=0", "management.health.redis.enabled=false"})
@AutoConfigureMockMvc
@Import(AdminContentLifecycleTransactionIntegrationTest.FailingRevisionRepositoryConfiguration.class)
@ActiveProfiles({"test", "admin-content-lifecycle-rollback-test"})
class AdminContentLifecycleTransactionIntegrationTest {
    private static final PostgreSQLContainer POSTGRES = postgresContainer();

    static {
        POSTGRES.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContentItemRepository contentItems;

    @Autowired
    private ContentRevisionRepository contentRevisions;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void prepareDatabase() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas("operations")
                .createSchemas(true)
                .load()
                .migrate();
        jdbc.execute("""
                TRUNCATE publishing.content_revisions,
                    publishing.content_rendered_headings,
                    publishing.content_render_snapshots,
                    publishing.content_items
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void publishPostRollsBackContentItemWhenRevisionSaveFails() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        contentItems.save(item);
        String path = "/admin/content/" + item.id().value();

        mockMvc.perform(post(path + "/publish")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(path + "/edit?publishFailed=true"));

        var reloaded = contentItems.findById(item.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(reloaded.renderSnapshot()).isEmpty();
        assertThat(contentRevisions.findByContentId(item.id())).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM publishing.content_revisions WHERE content_item_id = ?",
                Integer.class,
                item.id().value())).isZero();
    }

    private static PostgreSQLContainer postgresContainer() {
        PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
        postgres.withDatabaseName("persefonia_admin_content_rollback");
        postgres.withUsername("persefonia");
        postgres.withPassword("persefonia_dev");
        return postgres;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingRevisionRepositoryConfiguration {
        @Bean
        @Primary
        @Profile("admin-content-lifecycle-rollback-test")
        ContentRevisionRepository failingContentRevisionRepository(JdbcContentRevisionRepositoryAdapter delegate) {
            return new FailingContentRevisionRepository(delegate);
        }
    }

    private static final class FailingContentRevisionRepository implements ContentRevisionRepository {
        private final ContentRevisionRepository delegate;

        private FailingContentRevisionRepository(ContentRevisionRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public ContentRevision save(ContentRevision revision) {
            throw new ForcedRevisionFailure();
        }

        @Override
        public Optional<ContentRevision> findById(ContentRevisionId id) {
            return delegate.findById(id);
        }

        @Override
        public List<ContentRevision> findByContentId(ContentId contentId) {
            return delegate.findByContentId(contentId);
        }

        @Override
        public Optional<RevisionNumber> findLatestRevisionNumber(ContentId contentId) {
            return delegate.findLatestRevisionNumber(contentId);
        }
    }

    private static final class ForcedRevisionFailure extends IllegalStateException {
    }
}
