package dev.persefonia.app.contentpublishing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.command.ContentFieldUpdate;
import dev.persefonia.contentpublishing.application.command.PublishContentCommand;
import dev.persefonia.contentpublishing.application.command.UnpublishContentCommand;
import dev.persefonia.contentpublishing.application.command.UpdateContentDraftCommand;
import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        properties = {"management.server.port=0", "management.health.redis.enabled=false"},
        classes = dev.persefonia.app.PersefoniaApplication.class)
@ActiveProfiles("test")
class ContentDiscoveryIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-12T08:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-06-12T09:00:00Z");
    private static final ContentCommandActor OWNER = new ContentCommandActor(AdminIdentityRef.newId(), true, true);
    private static final SharedPostgresTestServer.Database POSTGRES = postgresContainer();

    @Autowired
    private TransactionalContentApplicationGateway gateway;

    @Autowired
    private ContentItemRepository contentItems;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("site.public-base-url", () -> "https://persefonia.test");
    }

    @Test
    void contentLifecycleSynchronizesDiscoveryCurrentProjectionAndSlugRedirect() {
        ContentItem item = completeDraft();
        contentItems.save(item);

        gateway.publishContent(new PublishContentCommand(OWNER, item.id(), PUBLISHED_AT, null));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_records WHERE action = 'content.published'", Long.class))
                .isEqualTo(1);

        Map<String, Object> publicProjection = discoveryProjection(item.id());
        assertThat(publicProjection)
                .containsEntry("public_url", "/en/articles/discovery-sync")
                .containsEntry("canonical_url", "https://persefonia.test/en/articles/discovery-sync")
                .containsEntry("indexing_policy", "INDEX")
                .containsEntry("search_eligibility", "ELIGIBLE");

        gateway.updateDraft(new UpdateContentDraftCommand(
                OWNER,
                item.id(),
                ContentFieldUpdate.set(Slug.of("discovery-sync-updated")),
                ContentFieldUpdate.unchanged(),
                ContentFieldUpdate.unchanged(),
                ContentFieldUpdate.unchanged(),
                ContentFieldUpdate.set(ContentMetadata.withCanonicalPath(
                        CanonicalPath.of("/articles/discovery-sync-updated"))),
                ContentFieldUpdate.set(ContentVisibility.UNLISTED),
                PUBLISHED_AT.plusSeconds(60)));

        Map<String, Object> unlistedProjection = discoveryProjection(item.id());
        assertThat(unlistedProjection)
                .containsEntry("public_url", "/en/articles/discovery-sync-updated")
                .containsEntry("canonical_url", "https://persefonia.test/en/articles/discovery-sync-updated")
                .containsEntry("indexing_policy", "NO_INDEX")
                .containsEntry("search_eligibility", "NOT_ELIGIBLE")
                .containsEntry("sitemap_eligibility", "NOT_ELIGIBLE")
                .containsEntry("feed_eligibility", "NOT_ELIGIBLE");

        assertThat(jdbc.queryForMap("""
                SELECT source_url, target_url, status_code, reason
                FROM discovery.redirect_rules
                WHERE source_entity_id = ?
                """, item.id().value()))
                .containsEntry("source_url", "/en/articles/discovery-sync")
                .containsEntry("target_url", "/en/articles/discovery-sync-updated")
                .containsEntry("status_code", 301)
                .containsEntry("reason", "SLUG_CHANGED");

        gateway.unpublishContent(new UnpublishContentCommand(OWNER, item.id(), PUBLISHED_AT.plusSeconds(120)));

        Integer remaining = jdbc.queryForObject("""
                SELECT count(*) FROM discovery.discoverable_resources WHERE source_entity_id = ?
                """, Integer.class, item.id().value());
        assertThat(remaining).isZero();
    }

    @Test
    void publishedUnlistedSlugChangePersistsSlugRedirect() {
        ContentItem item = completeDraft(ContentVisibility.UNLISTED);
        contentItems.save(item);

        gateway.publishContent(new PublishContentCommand(OWNER, item.id(), PUBLISHED_AT, null));

        gateway.updateDraft(new UpdateContentDraftCommand(
                OWNER,
                item.id(),
                ContentFieldUpdate.set(Slug.of("discovery-sync-unlisted-updated")),
                ContentFieldUpdate.unchanged(),
                ContentFieldUpdate.unchanged(),
                ContentFieldUpdate.unchanged(),
                ContentFieldUpdate.unchanged(),
                ContentFieldUpdate.unchanged(),
                PUBLISHED_AT.plusSeconds(60)));

        assertThat(jdbc.queryForMap("""
                SELECT source_url, target_url, status_code, reason
                FROM discovery.redirect_rules
                WHERE source_entity_id = ?
                """, item.id().value()))
                .containsEntry("source_url", "/en/articles/discovery-sync")
                .containsEntry("target_url", "/en/articles/discovery-sync-unlisted-updated")
                .containsEntry("status_code", 301)
                .containsEntry("reason", "SLUG_CHANGED");
    }

    @Test
    void mandatoryAuditFailureRollsBackPublishRevisionAndDiscoveryProjection() {
        ContentItem item = completeDraft();
        contentItems.save(item);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    jdbc.execute("""
                            ALTER TABLE audit.audit_records
                            ADD CONSTRAINT reject_content_audit_test CHECK (false)
                            """);
                    gateway.publishContent(new PublishContentCommand(OWNER, item.id(), PUBLISHED_AT, null));
                }))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(contentItems.findById(item.id()).orElseThrow().status())
                .isEqualTo(dev.persefonia.contentpublishing.domain.content.ContentStatus.DRAFT);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM publishing.content_revisions WHERE content_item_id = ?",
                Long.class, item.id().value())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM discovery.discoverable_resources WHERE source_entity_id = ?",
                Long.class, item.id().value())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_records", Long.class)).isZero();
    }

    private Map<String, Object> discoveryProjection(ContentId contentId) {
        return jdbc.queryForMap("""
                SELECT public_url, canonical_url, indexing_policy, search_eligibility,
                    sitemap_eligibility, feed_eligibility
                FROM discovery.discoverable_resources
                WHERE source_entity_id = ?
                """, contentId.value());
    }

    private static ContentItem completeDraft() {
        return completeDraft(ContentVisibility.PUBLIC);
    }

    private static ContentItem completeDraft(ContentVisibility visibility) {
        ContentItem item = ContentItem.createDraft(
                ContentId.newId(), ContentType.ARTICLE, visibility, ContentLanguage.EN, CREATED_AT);
        item.changeSlug(Slug.of("discovery-sync"), CREATED_AT);
        item.changeTitle(Title.of("Discovery sync"), CREATED_AT);
        item.changeSummary(Summary.of("Discovery integration proof."), CREATED_AT);
        item.changeMarkdownSource(MarkdownSource.of("# Discovery sync"), CREATED_AT);
        item.changeMetadata(ContentMetadata.withCanonicalPath(CanonicalPath.of("/articles/discovery-sync")), CREATED_AT);
        return item;
    }

    private static SharedPostgresTestServer.Database postgresContainer() {
        SharedPostgresTestServer.Database postgres = SharedPostgresTestServer.integrationDatabase();
        postgres.withDatabaseName("persefonia_content_discovery");
        postgres.withUsername("persefonia");
        postgres.withPassword("persefonia_dev");
        return postgres;
    }
}
