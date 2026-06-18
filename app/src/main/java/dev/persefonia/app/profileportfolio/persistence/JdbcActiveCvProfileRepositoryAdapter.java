package dev.persefonia.app.profileportfolio.persistence;

import dev.persefonia.profileportfolio.domain.cv.ActiveCvDocument;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfile;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileId;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileRepository;
import dev.persefonia.profileportfolio.domain.cv.CvDisplayLabel;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcActiveCvProfileRepositoryAdapter implements ActiveCvProfileRepository {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final ObjectProvider<TransactionTemplate> transactions;
    private final ActiveCvProfilePersistenceMapper mapper = new ActiveCvProfilePersistenceMapper();

    JdbcActiveCvProfileRepositoryAdapter(
            ObjectProvider<NamedParameterJdbcTemplate> jdbc,
            ObjectProvider<TransactionTemplate> transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public Optional<ActiveCvProfile> findSingleton() {
        return loadProfile("singleton_key = true", Map.of());
    }

    @Override
    public ActiveCvProfile save(ActiveCvProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return transactionTemplate().execute(status -> {
            long expectedVersion = currentVersion(profile.id().value())
                    .orElseThrow(() -> new PortfolioPersistenceException(
                            "Active CV profile does not exist: " + profile.id().value()));
            if (profile.version().value() <= expectedVersion) {
                throw new OptimisticLockingFailureException(
                        "Active CV profile save is stale for id " + profile.id().value());
            }
            updateProfile(profile, expectedVersion);
            replaceDocuments(profile);
            return findById(profile.id()).orElseThrow(() -> new PortfolioPersistenceException(
                    "Saved active CV profile could not be reloaded: " + profile.id().value()));
        });
    }

    Optional<ActiveCvProfile> findById(ActiveCvProfileId id) {
        Objects.requireNonNull(id, "id");
        return loadProfile("id = :id", Map.of("id", id.value()));
    }

    private Optional<Long> currentVersion(UUID id) {
        return jdbc().query("""
                SELECT version
                FROM portfolio.active_cv_profiles
                WHERE id = :id
                """, Map.of("id", id), (resultSet, rowNumber) -> resultSet.getLong("version")).stream().findFirst();
    }

    private void updateProfile(ActiveCvProfile profile, long expectedVersion) {
        int updated = jdbc().update("""
                UPDATE portfolio.active_cv_profiles
                SET updated_at = :updatedAt,
                    version = :version
                WHERE id = :id AND version = :expectedVersion
                """, new MapSqlParameterSource()
                .addValue("id", profile.id().value())
                .addValue("updatedAt", Timestamp.from(profile.updatedAt()))
                .addValue("version", profile.version().value())
                .addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                    "Active CV profile save is stale for id " + profile.id().value());
        }
    }

    private void replaceDocuments(ActiveCvProfile profile) {
        jdbc().update("""
                DELETE FROM portfolio.active_cv_documents
                WHERE active_cv_profile_id = :profileId
                """, Map.of("profileId", profile.id().value()));
        List<ActiveCvDocument> documents = profile.documents();
        if (documents.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = documents.stream()
                .map(document -> new MapSqlParameterSource()
                        .addValue("id", document.id().value())
                        .addValue("profileId", profile.id().value())
                        .addValue("language", document.language().name())
                        .addValue("assetId", document.mediaAssetId().value())
                        .addValue("displayLabel", document.displayLabel() == null
                                ? null
                                : document.displayLabel().value())
                        .addValue("selectedAt", Timestamp.from(document.selectedAt()))
                        .addValue("createdAt", Timestamp.from(document.createdAt()))
                        .addValue("updatedAt", Timestamp.from(document.updatedAt())))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO portfolio.active_cv_documents (
                    id, active_cv_profile_id, language, asset_id, display_label,
                    selected_at, created_at, updated_at
                ) VALUES (
                    :id, :profileId, :language, :assetId, :displayLabel,
                    :selectedAt, :createdAt, :updatedAt
                )
                """, batch);
    }

    private Optional<ActiveCvProfile> loadProfile(String whereClause, Map<String, Object> params) {
        String sql = """
                SELECT id, created_at, updated_at, version
                FROM portfolio.active_cv_profiles
                WHERE %s
                """.formatted(whereClause);
        return jdbc().query(sql, params, (resultSet, rowNumber) ->
                mapper.toDomain(resultSet, loadDocuments(resultSet.getObject("id", UUID.class)))).stream().findFirst();
    }

    private List<ActiveCvDocument> loadDocuments(UUID profileId) {
        return jdbc().query("""
                SELECT id, language, asset_id, display_label, selected_at, created_at, updated_at
                FROM portfolio.active_cv_documents
                WHERE active_cv_profile_id = :profileId
                ORDER BY language DESC
                """, Map.of("profileId", profileId), (resultSet, rowNumber) -> mapper.document(resultSet));
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new PortfolioPersistenceException("JDBC active CV profile repository is not available.");
        }
        return available;
    }

    private TransactionTemplate transactionTemplate() {
        TransactionTemplate available = transactions.getIfAvailable();
        if (available == null) {
            throw new PortfolioPersistenceException("JDBC transaction infrastructure is not available.");
        }
        return available;
    }
}
