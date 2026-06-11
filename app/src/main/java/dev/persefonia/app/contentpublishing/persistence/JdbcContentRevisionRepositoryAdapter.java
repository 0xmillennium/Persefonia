package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.revision.port.ContentRevisionRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcContentRevisionRepositoryAdapter implements ContentRevisionRepository {
    private final ObjectProvider<SpringDataContentRevisionRows> rootRows;
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final ObjectProvider<TransactionTemplate> transactions;
    private final ContentRevisionPersistenceMapper mapper = new ContentRevisionPersistenceMapper();

    JdbcContentRevisionRepositoryAdapter(
            ObjectProvider<SpringDataContentRevisionRows> rootRows,
            ObjectProvider<NamedParameterJdbcTemplate> jdbc,
            ObjectProvider<TransactionTemplate> transactions) {
        this.rootRows = rootRows;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public ContentRevision save(ContentRevision revision) {
        Objects.requireNonNull(revision, "revision");
        ContentRevisionPersistenceEntity entity = mapper.toEntity(revision);
        return transactionTemplate().execute(status -> {
            insert(entity);
            return findById(revision.id()).orElseThrow(() -> new ContentPublishingPersistenceException(
                    "Saved content revision could not be reloaded: " + revision.id().value()));
        });
    }

    @Override
    public Optional<ContentRevision> findById(ContentRevisionId id) {
        Objects.requireNonNull(id, "id");
        return rootRows().findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<ContentRevision> findByContentId(ContentId contentId) {
        Objects.requireNonNull(contentId, "contentId");
        return rowQueries().findByContentId(contentId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<RevisionNumber> findLatestRevisionNumber(ContentId contentId) {
        Objects.requireNonNull(contentId, "contentId");
        return rowQueries().findLatestRevisionNumber(contentId);
    }

    private void insert(ContentRevisionPersistenceEntity entity) {
        jdbc().update("""
                INSERT INTO publishing.content_revisions
                    (id, content_item_id, revision_number, revision_type, title, slug, summary,
                     markdown_source, rendered_html, meta_title, meta_description, canonical_path,
                     og_title, og_description, og_image_asset_id, created_by_admin_ref, created_at, change_note)
                VALUES
                    (:id, :contentItemId, :revisionNumber, :revisionType, :title, :slug, :summary,
                     :markdownSource, :renderedHtml, :metaTitle, :metaDescription, :canonicalPath,
                     :ogTitle, :ogDescription, :ogImageAssetId, :createdByAdminRef, :createdAt, :changeNote)
                """,
                new MapSqlParameterSource()
                        .addValue("id", entity.id())
                        .addValue("contentItemId", entity.contentItemId())
                        .addValue("revisionNumber", entity.revisionNumber())
                        .addValue("revisionType", entity.revisionType())
                        .addValue("title", entity.title())
                        .addValue("slug", entity.slug())
                        .addValue("summary", entity.summary())
                        .addValue("markdownSource", entity.markdownSource())
                        .addValue("renderedHtml", entity.renderedHtml())
                        .addValue("metaTitle", entity.metaTitle())
                        .addValue("metaDescription", entity.metaDescription())
                        .addValue("canonicalPath", entity.canonicalPath())
                        .addValue("ogTitle", entity.ogTitle())
                        .addValue("ogDescription", entity.ogDescription())
                        .addValue("ogImageAssetId", entity.ogImageAssetId())
                        .addValue("createdByAdminRef", entity.createdByAdminRef())
                        .addValue("createdAt", Timestamp.from(entity.createdAt()))
                        .addValue("changeNote", entity.changeNote()));
    }

    private SpringDataContentRevisionRows rootRows() {
        return required(rootRows, "Spring Data JDBC content revision rows are not available.");
    }

    private ContentRevisionRowQueries rowQueries() {
        return new ContentRevisionRowQueries(jdbc());
    }

    private NamedParameterJdbcTemplate jdbc() {
        return required(jdbc, "JDBC content publishing infrastructure is not available.");
    }

    private TransactionTemplate transactionTemplate() {
        return required(transactions, "JDBC transaction infrastructure is not available.");
    }

    private <T> T required(ObjectProvider<T> provider, String message) {
        T dependency = provider.getIfAvailable();
        if (dependency == null) {
            throw new ContentPublishingPersistenceException(message);
        }
        return dependency;
    }
}
