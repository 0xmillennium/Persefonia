package dev.persefonia.app.profileportfolio.persistence;

import dev.persefonia.profileportfolio.domain.profile.CurrentFocusItem;
import dev.persefonia.profileportfolio.domain.profile.EducationSummary;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLink;
import dev.persefonia.profileportfolio.domain.profile.FocusAreaDescription;
import dev.persefonia.profileportfolio.domain.profile.LocationText;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfile;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfileRepository;
import dev.persefonia.profileportfolio.domain.profile.ProfileId;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalization;
import dev.persefonia.profileportfolio.domain.profile.TechnicalFocusArea;
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
public class JdbcPersonalProfileRepositoryAdapter implements PersonalProfileRepository {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final ObjectProvider<TransactionTemplate> transactions;
    private final PersonalProfilePersistenceMapper mapper = new PersonalProfilePersistenceMapper();

    JdbcPersonalProfileRepositoryAdapter(
            ObjectProvider<NamedParameterJdbcTemplate> jdbc,
            ObjectProvider<TransactionTemplate> transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public PersonalProfile save(PersonalProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return transactionTemplate().execute(status -> {
            Optional<Long> currentVersion = currentVersion(profile.id().value());
            if (currentVersion.isEmpty()) {
                insertProfile(profile);
            } else {
                updateProfile(profile, currentVersion.get());
            }
            replaceChildren(profile);
            return findById(profile.id()).orElseThrow(() -> new PortfolioPersistenceException(
                    "Saved personal profile could not be reloaded: " + profile.id().value()));
        });
    }

    @Override
    public Optional<PersonalProfile> findById(ProfileId id) {
        Objects.requireNonNull(id, "id");
        return loadProfile("id = :id", Map.of("id", id.value()));
    }

    @Override
    public Optional<PersonalProfile> findActiveProfile() {
        return loadProfile("active = true", Map.of());
    }

    private Optional<Long> currentVersion(UUID id) {
        return jdbc().query("""
                SELECT version
                FROM portfolio.personal_profiles
                WHERE id = :id
                """, Map.of("id", id), (resultSet, rowNumber) -> resultSet.getLong("version")).stream().findFirst();
    }

    private void insertProfile(PersonalProfile profile) {
        jdbc().update("""
                INSERT INTO portfolio.personal_profiles (
                    id, display_name, active, created_at, updated_at, version
                ) VALUES (
                    :id, :displayName, :active, :createdAt, :updatedAt, :version
                )
                """, parameters(profile));
    }

    private void updateProfile(PersonalProfile profile, long expectedVersion) {
        if (profile.version().value() <= expectedVersion) {
            throw new OptimisticLockingFailureException("Personal profile save is stale for id " + profile.id().value());
        }
        int updated = jdbc().update("""
                UPDATE portfolio.personal_profiles
                SET display_name = :displayName,
                    active = :active,
                    updated_at = :updatedAt,
                    version = :version
                WHERE id = :id AND version = :expectedVersion
                """, parameters(profile).addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Personal profile save is stale for id " + profile.id().value());
        }
    }

    private MapSqlParameterSource parameters(PersonalProfile profile) {
        return new MapSqlParameterSource()
                .addValue("id", profile.id().value())
                .addValue("displayName", profile.displayName().value())
                .addValue("active", profile.active())
                .addValue("createdAt", Timestamp.from(profile.createdAt()))
                .addValue("updatedAt", Timestamp.from(profile.updatedAt()))
                .addValue("version", profile.version().value());
    }

    private void replaceChildren(PersonalProfile profile) {
        jdbc().update("""
                DELETE FROM portfolio.profile_localizations
                WHERE profile_id = :profileId
                """, Map.of("profileId", profile.id().value()));
        jdbc().update("""
                DELETE FROM portfolio.external_profile_links
                WHERE profile_id = :profileId
                """, Map.of("profileId", profile.id().value()));
        insertLocalizations(profile);
        insertExternalLinks(profile);
    }

    private void insertLocalizations(PersonalProfile profile) {
        MapSqlParameterSource[] localizationBatch = profile.localizations().stream()
                .map(localization -> new MapSqlParameterSource()
                        .addValue("id", localization.id().value())
                        .addValue("profileId", profile.id().value())
                        .addValue("language", localization.language().name())
                        .addValue("shortBio", localization.shortBio().value())
                        .addValue("longBio", localization.longBio().value())
                        .addValue("locationText", localization.locationText() == null ? null : localization.locationText().value()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO portfolio.profile_localizations (
                    id, profile_id, language, short_bio, long_bio, location_text
                ) VALUES (
                    :id, :profileId, :language, :shortBio, :longBio, :locationText
                )
                """, localizationBatch);
        for (ProfileLocalization localization : profile.localizations()) {
            insertFocusAreas(localization);
            insertEducationSummaries(localization);
            insertCurrentFocusItems(localization);
        }
    }

    private void insertExternalLinks(PersonalProfile profile) {
        MapSqlParameterSource[] batch = profile.externalLinks().stream()
                .map(link -> new MapSqlParameterSource()
                        .addValue("id", link.id().value())
                        .addValue("profileId", profile.id().value())
                        .addValue("label", link.label().value())
                        .addValue("url", link.url().value())
                        .addValue("sortOrder", link.sortOrder().value()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO portfolio.external_profile_links (
                    id, profile_id, label, url, sort_order
                ) VALUES (
                    :id, :profileId, :label, :url, :sortOrder
                )
                """, batch);
    }

    private void insertFocusAreas(ProfileLocalization localization) {
        MapSqlParameterSource[] batch = localization.technicalFocusAreas().stream()
                .map(area -> new MapSqlParameterSource()
                        .addValue("id", area.id().value())
                        .addValue("profileLocalizationId", localization.id().value())
                        .addValue("name", area.name().value())
                        .addValue("description", area.description() == null ? null : area.description().value())
                        .addValue("sortOrder", area.sortOrder().value()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO portfolio.technical_focus_areas (
                    id, profile_localization_id, name, description, sort_order
                ) VALUES (
                    :id, :profileLocalizationId, :name, :description, :sortOrder
                )
                """, batch);
    }

    private void insertEducationSummaries(ProfileLocalization localization) {
        MapSqlParameterSource[] batch = localization.educationSummaries().stream()
                .map(summary -> new MapSqlParameterSource()
                        .addValue("id", summary.id().value())
                        .addValue("profileLocalizationId", localization.id().value())
                        .addValue("institution", summary.institution().value())
                        .addValue("program", summary.program().value())
                        .addValue("description", summary.description() == null ? null : summary.description().value())
                        .addValue("sortOrder", summary.sortOrder().value()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO portfolio.education_summaries (
                    id, profile_localization_id, institution, program, description, sort_order
                ) VALUES (
                    :id, :profileLocalizationId, :institution, :program, :description, :sortOrder
                )
                """, batch);
    }

    private void insertCurrentFocusItems(ProfileLocalization localization) {
        MapSqlParameterSource[] batch = localization.currentFocusItems().stream()
                .map(item -> new MapSqlParameterSource()
                        .addValue("id", item.id().value())
                        .addValue("profileLocalizationId", localization.id().value())
                        .addValue("text", item.text().value())
                        .addValue("sortOrder", item.sortOrder().value()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO portfolio.current_focus_items (
                    id, profile_localization_id, text, sort_order
                ) VALUES (
                    :id, :profileLocalizationId, :text, :sortOrder
                )
                """, batch);
    }

    private Optional<PersonalProfile> loadProfile(String whereClause, Map<String, Object> params) {
        String sql = """
                SELECT id, display_name, active, created_at, updated_at, version
                FROM portfolio.personal_profiles
                WHERE %s
                """.formatted(whereClause);
        List<PersonalProfile> rows = jdbc().query(sql, params, (resultSet, rowNumber) -> {
            UUID profileId = resultSet.getObject("id", UUID.class);
            return mapper.toDomain(resultSet, loadLocalizations(profileId), loadExternalLinks(profileId));
        });
        return rows.stream().findFirst();
    }

    private List<ProfileLocalization> loadLocalizations(UUID profileId) {
        return jdbc().query("""
                SELECT id, language, short_bio, long_bio, location_text
                FROM portfolio.profile_localizations
                WHERE profile_id = :profileId
                ORDER BY language
                """, Map.of("profileId", profileId), (resultSet, rowNumber) -> {
            UUID localizationId = resultSet.getObject("id", UUID.class);
            return mapper.localization(
                    resultSet,
                    loadFocusAreas(localizationId),
                    loadEducationSummaries(localizationId),
                    loadCurrentFocusItems(localizationId));
        });
    }

    private List<ExternalProfileLink> loadExternalLinks(UUID profileId) {
        return jdbc().query("""
                SELECT id, label, url, sort_order
                FROM portfolio.external_profile_links
                WHERE profile_id = :profileId
                ORDER BY sort_order
                """, Map.of("profileId", profileId), (resultSet, rowNumber) -> mapper.externalLink(resultSet));
    }

    private List<TechnicalFocusArea> loadFocusAreas(UUID localizationId) {
        return jdbc().query("""
                SELECT id, name, description, sort_order
                FROM portfolio.technical_focus_areas
                WHERE profile_localization_id = :localizationId
                ORDER BY sort_order
                """, Map.of("localizationId", localizationId), (resultSet, rowNumber) -> mapper.focusArea(resultSet));
    }

    private List<EducationSummary> loadEducationSummaries(UUID localizationId) {
        return jdbc().query("""
                SELECT id, institution, program, description, sort_order
                FROM portfolio.education_summaries
                WHERE profile_localization_id = :localizationId
                ORDER BY sort_order
                """, Map.of("localizationId", localizationId), (resultSet, rowNumber) -> mapper.educationSummary(resultSet));
    }

    private List<CurrentFocusItem> loadCurrentFocusItems(UUID localizationId) {
        return jdbc().query("""
                SELECT id, text, sort_order
                FROM portfolio.current_focus_items
                WHERE profile_localization_id = :localizationId
                ORDER BY sort_order
                """, Map.of("localizationId", localizationId), (resultSet, rowNumber) -> mapper.currentFocusItem(resultSet));
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new PortfolioPersistenceException("JDBC personal profile repository is not available.");
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
