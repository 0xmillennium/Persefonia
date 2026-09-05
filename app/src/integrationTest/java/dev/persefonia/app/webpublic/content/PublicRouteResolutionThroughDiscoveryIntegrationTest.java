package dev.persefonia.app.webpublic.content;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.contentpublishing.application.TransactionalContentApplicationGateway;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

@SpringBootTest(
        properties = {"management.server.port=0", "management.health.redis.enabled=false"},
        classes = dev.persefonia.app.PersefoniaApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class PublicRouteResolutionThroughDiscoveryIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-12T08:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-06-12T09:00:00Z");
    private static final ContentCommandActor OWNER = new ContentCommandActor(AdminIdentityRef.newId(), true, true);
    private static final SharedPostgresTestServer.Database POSTGRES = postgresContainer();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionalContentApplicationGateway gateway;

    @Autowired
    private ContentItemRepository contentItems;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("site.public-base-url", () -> "https://persefonia.test");
    }

    @BeforeEach
    void prepareDatabase() {        jdbc.execute("""
                TRUNCATE discovery.redirect_rules,
                    discovery.discoverable_resources,
                    publishing.content_revisions,
                    publishing.content_rendered_headings,
                    publishing.content_render_snapshots,
                    publishing.content_items
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void publishedPublicContentResolvesThroughDiscoveryToPublicPage() throws Exception {
        ContentItem item = publish(completeDraft(ContentVisibility.PUBLIC, "discovery-http"));

        mockMvc.perform(get("/en/articles/discovery-http"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Discovery HTTP")))
                .andExpect(content().string(containsString("<h1 id=\"discovery-http\">Discovery HTTP</h1>")))
                .andExpect(content().string(not(containsString("markdownSource"))))
                .andExpect(content().string(not(containsString(item.id().value().toString()))));
    }

    @Test
    void publishedUnlistedContentResolvesThroughDiscoveryAndKeepsNoindex() throws Exception {
        publish(completeDraft(ContentVisibility.UNLISTED, "discovery-unlisted"));

        mockMvc.perform(get("/en/articles/discovery-unlisted"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(containsString("<h1 id=\"discovery-http\">Discovery HTTP</h1>")));
    }

    @Test
    void unpublishedPrivateAndMissingDiscoveryRoutesReturnSafeNotFound() throws Exception {
        ContentItem unpublished = publish(completeDraft(ContentVisibility.PUBLIC, "discovery-unpublished"));
        gateway.unpublishContent(new UnpublishContentCommand(OWNER, unpublished.id(), PUBLISHED_AT.plusSeconds(60)));
        publish(completeDraft(ContentVisibility.PRIVATE, "discovery-private"));

        assertSafeNotFound("/en/articles/discovery-unpublished");
        assertSafeNotFound("/en/articles/discovery-private");
        assertSafeNotFound("/en/articles/discovery-missing");
    }

    @Test
    void slugRedirectAndRedirectBeforeResourceAreResolvedAtHttpLevel() throws Exception {
        ContentItem item = publish(completeDraft(ContentVisibility.PUBLIC, "discovery-old"));
        gateway.updateDraft(new UpdateContentDraftCommand(
                OWNER,
                item.id(),
                ContentFieldUpdate.set(Slug.of("discovery-new")),
                ContentFieldUpdate.unchanged(),
                ContentFieldUpdate.unchanged(),
                ContentFieldUpdate.unchanged(),
                ContentFieldUpdate.set(ContentMetadata.withCanonicalPath(CanonicalPath.of("/articles/discovery-new"))),
                ContentFieldUpdate.unchanged(),
                PUBLISHED_AT.plusSeconds(60)));

        mockMvc.perform(get("/en/articles/discovery-old"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/en/articles/discovery-new"))
                .andExpect(content().string(not(containsString("Discovery HTTP body"))));

        insertRedirect("/en/articles/discovery-new", "/en/articles/redirect-wins", item.id());

        mockMvc.perform(get("/en/articles/discovery-new"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/en/articles/redirect-wins"))
                .andExpect(content().string(not(containsString("Discovery HTTP body"))));
    }

    @Test
    void staleDiscoveryProjectionReturnsSafeNotFound() throws Exception {
        ContentItem item = publish(completeDraft(ContentVisibility.PUBLIC, "discovery-current"));
        jdbc.update("""
                UPDATE discovery.discoverable_resources
                SET public_url = ?
                WHERE source_entity_id = ?
                """, "/en/articles/discovery-stale", item.id().value());

        assertSafeNotFound("/en/articles/discovery-stale");
    }

    private ContentItem publish(ContentItem item) {
        contentItems.save(item);
        gateway.publishContent(new PublishContentCommand(OWNER, item.id(), PUBLISHED_AT, null));
        return contentItems.findById(item.id()).orElseThrow();
    }

    private void assertSafeNotFound(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("The page you requested was not found.")))
                .andExpect(content().string(containsString("noindex")))
                .andExpect(content().string(not(containsString("source_entity_id"))))
                .andExpect(content().string(not(containsString("stack trace"))))
                .andExpect(content().string(not(containsString("/admin/content"))))
                .andExpect(content().string(not(containsString("/preview"))))
                .andExpect(content().string(not(containsString("/revisions"))));
    }

    private void insertRedirect(String sourcePath, String targetPath, ContentId contentId) {
        jdbc.update("""
                INSERT INTO discovery.redirect_rules (
                    id, source_url, target_url, status_code, reason, source_context, source_type, source_entity_id,
                    active, created_at, updated_at, version
) VALUES (?, ?, ?, 301, 'SLUG_CHANGED', 'CONTENT_PUBLISHING', 'CONTENT_ITEM', ?, true, ?, ?, 0)
                """,
                UUID.randomUUID(),
                sourcePath,
                targetPath,
                contentId.value(),
                Timestamp.from(PUBLISHED_AT),
                Timestamp.from(PUBLISHED_AT));
    }

    private static ContentItem completeDraft(ContentVisibility visibility, String slug) {
        ContentItem item = ContentItem.createDraft(
                ContentId.newId(), ContentType.ARTICLE, visibility, ContentLanguage.EN, CREATED_AT);
        item.changeSlug(Slug.of(slug), CREATED_AT);
        item.changeTitle(Title.of("Discovery HTTP"), CREATED_AT);
        item.changeSummary(Summary.of("Discovery HTTP summary."), CREATED_AT);
        item.changeMarkdownSource(MarkdownSource.of("# Discovery HTTP"), CREATED_AT);
        item.changeMetadata(ContentMetadata.withCanonicalPath(CanonicalPath.of("/articles/" + slug)), CREATED_AT);
        return item;
    }

    private static SharedPostgresTestServer.Database postgresContainer() {
        SharedPostgresTestServer.Database postgres = SharedPostgresTestServer.integrationDatabase();
        postgres.withDatabaseName("persefonia_public_route_discovery");
        postgres.withUsername("persefonia");
        postgres.withPassword("persefonia_dev");
        return postgres;
    }
}
