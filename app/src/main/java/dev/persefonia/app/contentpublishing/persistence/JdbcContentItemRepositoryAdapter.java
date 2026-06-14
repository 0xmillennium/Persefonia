package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcContentItemRepositoryAdapter implements ContentItemRepository {
    private static final String TAG_PERSISTENCE_DEFERRED =
            "Content tag persistence is not available until taxonomy/content tagging is implemented.";

    private final ObjectProvider<SpringDataContentItemRows> rootRows;
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final ObjectProvider<TransactionTemplate> transactions;
    private final ContentItemPersistenceMapper mapper = new ContentItemPersistenceMapper();

    JdbcContentItemRepositoryAdapter(
            ObjectProvider<SpringDataContentItemRows> rootRows,
            ObjectProvider<NamedParameterJdbcTemplate> jdbc,
            ObjectProvider<TransactionTemplate> transactions) {
        this.rootRows = rootRows;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public ContentItem save(ContentItem item) {
        Objects.requireNonNull(item, "item");
        if (!item.tagIds().isEmpty()) {
            throw new UnsupportedOperationException(TAG_PERSISTENCE_DEFERRED);
        }
        return transactionTemplate().execute(status -> {
            SpringDataContentItemRows rows = rootRows();
            Optional<ContentItemPersistenceEntity> existing = rows.findById(item.id().value());
            Long jdbcVersion = existing
                    .map(current -> jdbcVersionForSave(item, current.version()))
                    .orElse(null);
            ContentItemPersistenceEntity saved = rows.save(mapper.toEntity(item, jdbcVersion));
            replaceRenderSnapshot(saved.id(), item);
            return load(saved).orElseThrow(() -> new ContentPublishingPersistenceException(
                    "Saved content item could not be reloaded: " + saved.id()));
        });
    }

    @Override
    public Optional<ContentItem> findById(ContentId id) {
        Objects.requireNonNull(id, "id");
        return rootRows().findById(id.value()).flatMap(this::load);
    }

    @Override
    public Optional<ContentItem> findBySlugAndTypeAndLanguage(
            Slug slug,
            ContentType type,
            ContentLanguage language) {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(language, "language");
        return rowQueries().findBySlugAndTypeAndLanguage(slug.value(), type.name(), language.name()).flatMap(this::load);
    }

    @Override
    public Optional<ContentItem> findPublishedByRoute(ContentType type, Slug slug, ContentLanguage language) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(language, "language");
        return rowQueries().findPublishedByRoute(type.name(), slug.value(), language.name()).flatMap(this::load);
    }

    @Override
    public List<ContentItem> findDrafts() {
        return rowQueries().findDrafts().stream()
                .map(this::loadRequired)
                .toList();
    }

    @Override
    public List<ContentItem> findByStatus(ContentStatus status) {
        Objects.requireNonNull(status, "status");
        return rowQueries().findByStatus(status.name()).stream()
                .map(this::loadRequired)
                .toList();
    }

    @Override
    public boolean existsSlugInNamespace(ContentType type, ContentLanguage language, Slug slug) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
        return rowQueries().existsSlugInNamespace(type.name(), language.name(), slug.value());
    }

    private void replaceRenderSnapshot(UUID contentItemId, ContentItem item) {
        ContentItemRenderedHeadingTable headings = headings();
        ContentItemRenderSnapshotTable snapshots = snapshots();
        headings.deleteByContentItemId(contentItemId);
        snapshots.delete(contentItemId);
        item.renderSnapshot().ifPresent(snapshot -> {
            snapshots.insert(contentItemId, snapshot);
            headings.insertAll(contentItemId, snapshot.headings());
        });
    }

    private ContentItem loadRequired(ContentItemPersistenceEntity entity) {
        return load(entity).orElseThrow(() -> new ContentPublishingPersistenceException(
                "Content item could not be loaded: " + entity.id()));
    }

    private Optional<ContentItem> load(ContentItemPersistenceEntity entity) {
        ContentItemRenderSnapshotTable snapshots = snapshots();
        ContentItemRenderedHeadingTable headings = headings();
        ContentItemRenderSnapshotTable.Row snapshot = snapshots.findByContentItemId(entity.id()).orElse(null);
        List<ContentItemRenderedHeadingTable.Row> headingRows = snapshot == null
                ? List.of()
                : headings.findByContentItemId(entity.id());
        return Optional.of(mapper.toDomain(entity, snapshot, headingRows));
    }

    private SpringDataContentItemRows rootRows() {
        return required(rootRows, "Spring Data JDBC content item rows are not available.");
    }

    private ContentItemRowQueries rowQueries() {
        return new ContentItemRowQueries(jdbc());
    }

    private ContentItemRenderSnapshotTable snapshots() {
        return new ContentItemRenderSnapshotTable(jdbc());
    }

    private ContentItemRenderedHeadingTable headings() {
        return new ContentItemRenderedHeadingTable(jdbc());
    }

    private NamedParameterJdbcTemplate jdbc() {
        return required(jdbc, "JDBC content publishing infrastructure is not available.");
    }

    private TransactionTemplate transactionTemplate() {
        return required(transactions, "JDBC transaction infrastructure is not available.");
    }

    private Long jdbcVersionForSave(ContentItem item, Long currentVersion) {
        if (currentVersion == null) {
            throw new ContentPublishingPersistenceException(
                    "Existing content item has no optimistic lock version: " + item.id().value());
        }
        if (item.version().value() <= currentVersion) {
            throw new OptimisticLockingFailureException(
                    "Content item save is stale for id " + item.id().value());
        }
        return currentVersion;
    }

    private <T> T required(ObjectProvider<T> provider, String message) {
        T dependency = provider.getIfAvailable();
        if (dependency == null) {
            throw new ContentPublishingPersistenceException(message);
        }
        return dependency;
    }
}
