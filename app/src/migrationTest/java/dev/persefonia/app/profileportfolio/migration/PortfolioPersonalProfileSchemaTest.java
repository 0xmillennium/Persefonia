package dev.persefonia.app.profileportfolio.migration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortfolioPersonalProfileSchemaTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-16T10:00:00Z");

    @BeforeAll
    static void migrateDatabase() {
        PortfolioMigrationDatabase.start();
        PortfolioMigrationDatabase.cleanMigrate();
    }

    @BeforeEach
    void truncateProfiles() throws SQLException {
        PortfolioMigrationDatabase.truncatePortfolioMutableTables();
    }

    @Test
    void moreThanOneActiveProfileIsRejected() throws SQLException {
        insertProfile(UUID.randomUUID(), true);

        assertThatThrownBy(() -> insertProfile(UUID.randomUUID(), true))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void duplicateProfileLocalizationLanguageIsRejected() throws SQLException {
        UUID profileId = insertProfile(UUID.randomUUID(), false);
        insertLocalization(UUID.randomUUID(), profileId, "TR");

        assertThatThrownBy(() -> insertLocalization(UUID.randomUUID(), profileId, "TR"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void duplicateExternalProfileLinkSortOrderIsRejected() throws SQLException {
        UUID profileId = insertProfile(UUID.randomUUID(), false);
        insertExternalLink(UUID.randomUUID(), profileId, 1);

        assertThatThrownBy(() -> insertExternalLink(UUID.randomUUID(), profileId, 1))
                .isInstanceOf(SQLException.class);
    }

    private static UUID insertProfile(UUID id, boolean active) throws SQLException {
        PortfolioSql.update("""
                INSERT INTO portfolio.personal_profiles (id, display_name, active, created_at, updated_at, version)
                VALUES (?, 'Enes', ?, ?, ?, 0)
                """, id, active, NOW, NOW);
        return id;
    }

    private static void insertLocalization(UUID id, UUID profileId, String language) throws SQLException {
        PortfolioSql.update("""
                INSERT INTO portfolio.profile_localizations (id, profile_id, language, short_bio, long_bio)
                VALUES (?, ?, ?, 'Short', 'Long')
                """, id, profileId, language);
    }

    private static void insertExternalLink(UUID id, UUID profileId, int sortOrder) throws SQLException {
        PortfolioSql.update("""
                INSERT INTO portfolio.external_profile_links (id, profile_id, label, url, sort_order)
                VALUES (?, ?, 'GitHub', 'https://example.test', ?)
                """, id, profileId, sortOrder);
    }
}
