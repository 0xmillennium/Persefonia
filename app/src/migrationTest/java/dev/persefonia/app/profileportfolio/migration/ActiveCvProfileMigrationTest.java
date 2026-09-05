package dev.persefonia.app.profileportfolio.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActiveCvProfileMigrationTest {
    private static final UUID SEEDED_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-18T10:00:00Z");

    @BeforeAll
    static void migrateDatabase() {
        PortfolioMigrationDatabase.start();
        PortfolioMigrationDatabase.cleanMigrate();
    }

    @Test
    void singletonRowIsSeeded() throws SQLException {
        assertThat(PortfolioSql.count("""
                SELECT count(*)
                FROM portfolio.active_cv_profiles
                WHERE id = ?
                """, SEEDED_PROFILE_ID)).isEqualTo(1);
    }

    @Test
    void singletonUniquenessIsEnforced() {
        assertThatThrownBy(() -> PortfolioSql.update("""
                INSERT INTO portfolio.active_cv_profiles (id, singleton_key, created_at, updated_at, version)
                VALUES (?, true, ?, ?, 0)
                """, UUID.randomUUID(), NOW, NOW)).isInstanceOf(SQLException.class);
    }

    @Test
    void documentLanguageIsUniquePerProfile() throws SQLException {
        insertDocument(UUID.randomUUID(), "EN", UUID.randomUUID(), "CV");

        assertThatThrownBy(() -> insertDocument(UUID.randomUUID(), "EN", UUID.randomUUID(), "CV 2"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void assetIdHasNoForeignKeyToMediaAssets() throws SQLException {
        assertThat(PortfolioSchemaAssertions.foreignKeyCount(
                "active_cv_documents", "asset_id", "media", "assets")).isZero();
    }

    @Test
    void languageCheckIsEnforced() {
        assertThatThrownBy(() -> insertDocument(UUID.randomUUID(), "DE", UUID.randomUUID(), "CV"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void displayLabelMustBeNonblankWhenPresent() {
        assertThatThrownBy(() -> insertDocument(UUID.randomUUID(), "TR", UUID.randomUUID(), " "))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void versionCannotBeNegative() {
        assertThatThrownBy(() -> PortfolioSql.update("""
                UPDATE portfolio.active_cv_profiles
                SET version = -1
                WHERE id = ?
                """, SEEDED_PROFILE_ID)).isInstanceOf(SQLException.class);
    }

    @Test
    void createdAtCannotBeAfterUpdatedAt() {
        assertThatThrownBy(() -> PortfolioSql.update("""
                UPDATE portfolio.active_cv_profiles
                SET created_at = ?, updated_at = ?
                WHERE id = ?
                """, NOW.plusDays(1), NOW, SEEDED_PROFILE_ID)).isInstanceOf(SQLException.class);
    }

    private static void insertDocument(UUID id, String language, UUID assetId, String label) throws SQLException {
        PortfolioSql.update("""
                INSERT INTO portfolio.active_cv_documents (
                    id, active_cv_profile_id, language, asset_id, display_label,
                    selected_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, SEEDED_PROFILE_ID, language, assetId, label, NOW, NOW, NOW);
    }
}
