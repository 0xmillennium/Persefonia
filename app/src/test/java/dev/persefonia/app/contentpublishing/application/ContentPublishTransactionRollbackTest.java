package dev.persefonia.app.contentpublishing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.command.PublishContentCommand;
import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.revision.port.ContentRevisionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        classes = {dev.persefonia.app.PersefoniaApplication.class, ContentPublishTransactionRollbackTest.FailingRevisionConfiguration.class})
@ActiveProfiles({"test", "content-publish-rollback"})
class ContentPublishTransactionRollbackTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-12T08:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-06-12T09:00:00Z");
    private static final ContentCommandActor OWNER = new ContentCommandActor(AdminIdentityRef.newId(), true, true);
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("persefonia_publish_rollback")
            .withUsername("persefonia")
            .withPassword("persefonia_dev");

    static {
        POSTGRES.start();
    }

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
    void failedRevisionSaveRollsBackPublishedContentItem() {
        ContentItem item = completeDraft();
        contentItems.save(item);

        assertThatThrownBy(() -> gateway.publishContent(new PublishContentCommand(OWNER, item.id(), PUBLISHED_AT, null)))
                .isInstanceOf(ForcedRevisionFailure.class);

        ContentItem reloaded = contentItems.findById(item.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(reloaded.renderSnapshot()).isEmpty();
    }

    private ContentItem completeDraft() {
        ContentItem item = ContentItem.createDraft(
                ContentId.newId(), ContentType.ARTICLE, ContentVisibility.PUBLIC, ContentLanguage.EN, CREATED_AT);
        item.changeSlug(Slug.of("rollback-proof"), CREATED_AT);
        item.changeTitle(Title.of("Rollback proof"), CREATED_AT);
        item.changeSummary(Summary.of("Rollback transaction proof."), CREATED_AT);
        item.changeMarkdownSource(MarkdownSource.of("# Rollback proof"), CREATED_AT);
        item.changeMetadata(ContentMetadata.withCanonicalPath(CanonicalPath.of("/articles/rollback-proof")), CREATED_AT);
        return item;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingRevisionConfiguration {
        @Bean
        @Primary
        @Profile("content-publish-rollback")
        ContentRevisionRepository failingContentRevisionRepository() {
            return new ContentRevisionRepository() {
                @Override
                public ContentRevision save(ContentRevision revision) {
                    throw new ForcedRevisionFailure();
                }

                @Override
                public Optional<ContentRevision> findById(ContentRevisionId id) {
                    return Optional.empty();
                }

                @Override
                public List<ContentRevision> findByContentId(ContentId contentId) {
                    return List.of();
                }

                @Override
                public Optional<RevisionNumber> findLatestRevisionNumber(ContentId contentId) {
                    return Optional.empty();
                }
            };
        }
    }

    private static final class ForcedRevisionFailure extends RuntimeException {
    }
}
