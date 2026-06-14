package dev.persefonia.app.contentpublishing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.command.ContentFieldUpdate;
import dev.persefonia.contentpublishing.application.command.PublishContentCommand;
import dev.persefonia.contentpublishing.application.command.UpdateContentDraftCommand;
import dev.persefonia.contentpublishing.application.exception.ContentDiscoverySynchronizationException;
import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.ReadingTime;
import dev.persefonia.contentpublishing.domain.content.RenderedHeading;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.content.RendererVersion;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.discovery.application.port.CreateRedirectRulePort;
import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        properties = {"management.server.port=0", "management.health.redis.enabled=false"},
        classes = {
                dev.persefonia.app.PersefoniaApplication.class,
                ContentDiscoveryTransactionRollbackTest.FailingDiscoveryConfiguration.class
        })
@ActiveProfiles({"test", "content-discovery-rollback"})
class ContentDiscoveryTransactionRollbackTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-12T08:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-06-12T09:00:00Z");
    private static final ContentCommandActor OWNER = new ContentCommandActor(AdminIdentityRef.newId(), true, true);
    private static final PostgreSQLContainer POSTGRES = postgresContainer();

    static {
        POSTGRES.start();
    }

    @Autowired
    private TransactionalContentApplicationGateway gateway;

    @Autowired
    private ContentItemRepository contentItems;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FailingDiscoveryScenario discoveryScenario;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("site.public-base-url", () -> "https://persefonia.test");
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
                TRUNCATE discovery.redirect_rules,
                    discovery.discoverable_resources,
                    publishing.content_revisions,
                    publishing.content_rendered_headings,
                    publishing.content_render_snapshots,
                    publishing.content_items
                RESTART IDENTITY CASCADE
                """);
        discoveryScenario.reset();
    }

    @Test
    void discoveryProjectionFailureRollsBackPublishedContentItemAndRevision() {
        discoveryScenario.rejectProjection = true;
        ContentItem item = completeDraft();
        contentItems.save(item);

        assertThatThrownBy(() -> gateway.publishContent(new PublishContentCommand(OWNER, item.id(), PUBLISHED_AT, null)))
                .isInstanceOf(ContentDiscoverySynchronizationException.class)
                .hasMessageContaining("CONFLICT");

        ContentItem reloaded = contentItems.findById(item.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(reloaded.renderSnapshot()).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM publishing.content_revisions WHERE content_item_id = ?",
                Integer.class,
                item.id().value())).isZero();
    }

    @Test
    void unlistedRedirectFailureRollsBackSlugChangingCommand() {
        discoveryScenario.rejectRedirect = true;
        ContentItem item = completeDraft(ContentVisibility.UNLISTED);
        item.publish(renderSnapshot(), PUBLISHED_AT);
        contentItems.save(item);

        assertThatThrownBy(() -> gateway.updateDraft(new UpdateContentDraftCommand(
                        OWNER,
                        item.id(),
                        ContentFieldUpdate.set(Slug.of("discovery-rollback-updated")),
                        ContentFieldUpdate.unchanged(),
                        ContentFieldUpdate.unchanged(),
                        ContentFieldUpdate.unchanged(),
                        ContentFieldUpdate.unchanged(),
                        ContentFieldUpdate.unchanged(),
                        PUBLISHED_AT.plusSeconds(60))))
                .isInstanceOf(ContentDiscoverySynchronizationException.class)
                .hasMessageContaining("DUPLICATE_ACTIVE_SOURCE");

        ContentItem reloaded = contentItems.findById(item.id()).orElseThrow();
        assertThat(reloaded.slug().orElseThrow().value()).isEqualTo("discovery-rollback");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM discovery.redirect_rules WHERE source_entity_id = ?",
                Integer.class,
                item.id().value())).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingDiscoveryConfiguration {
        @Bean
        @Profile("content-discovery-rollback")
        FailingDiscoveryScenario failingDiscoveryScenario() {
            return new FailingDiscoveryScenario();
        }

        @Bean
        @Primary
        @Profile("content-discovery-rollback")
        UpdateDiscoverableResourcePort failingUpdateDiscoverableResourcePort(FailingDiscoveryScenario scenario) {
            return input -> scenario.rejectProjection
                    ? new DiscoverableResourceProjectionResult.Rejected(
                            DiscoverableResourceProjectionResult.Reason.CONFLICT)
                    : new DiscoverableResourceProjectionResult.Updated();
        }

        @Bean
        @Primary
        @Profile("content-discovery-rollback")
        CreateRedirectRulePort failingCreateRedirectRulePort(FailingDiscoveryScenario scenario) {
            return command -> scenario.rejectRedirect
                    ? new RedirectRuleCreationResult.Rejected(
                            RedirectRuleCreationResult.Reason.DUPLICATE_ACTIVE_SOURCE)
                    : new RedirectRuleCreationResult.Created();
        }
    }

    static final class FailingDiscoveryScenario {
        private boolean rejectProjection;
        private boolean rejectRedirect;

        private void reset() {
            rejectProjection = false;
            rejectRedirect = false;
        }
    }

    private static ContentItem completeDraft() {
        return completeDraft(ContentVisibility.PUBLIC);
    }

    private static ContentItem completeDraft(ContentVisibility visibility) {
        ContentItem item = ContentItem.createDraft(
                ContentId.newId(), ContentType.ARTICLE, visibility, ContentLanguage.EN, CREATED_AT);
        item.changeSlug(Slug.of("discovery-rollback"), CREATED_AT);
        item.changeTitle(Title.of("Discovery rollback"), CREATED_AT);
        item.changeSummary(Summary.of("Discovery rollback proof."), CREATED_AT);
        item.changeMarkdownSource(MarkdownSource.of("# Discovery rollback"), CREATED_AT);
        item.changeMetadata(ContentMetadata.withCanonicalPath(CanonicalPath.of("/articles/discovery-rollback")), CREATED_AT);
        return item;
    }

    private static PostgreSQLContainer postgresContainer() {
        PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
        postgres.withDatabaseName("persefonia_content_discovery_rollback");
        postgres.withUsername("persefonia");
        postgres.withPassword("persefonia_dev");
        return postgres;
    }

    private static ContentRenderSnapshot renderSnapshot() {
        return ContentRenderSnapshot.of(
                RenderedHtml.sanitized("<h1>Discovery rollback</h1>"),
                PUBLISHED_AT,
                RendererVersion.of("test-renderer"),
                ReadingTime.minutes(1),
                false,
                List.of(RenderedHeading.of(1, "Discovery rollback", "discovery-rollback", 1)));
    }
}
