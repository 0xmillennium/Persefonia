package dev.persefonia.app.profileportfolio.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PortfolioCrossContextBoundarySchemaTest {
    @BeforeAll
    static void migrateDatabase() {
        PortfolioMigrationDatabase.start();
        PortfolioMigrationDatabase.cleanMigrate();
    }

    @Test
    void projectTagsReferencePortfolioProjectsButNotTaxonomyTags() throws SQLException {
        assertThat(PortfolioSchemaAssertions.foreignKeyCount("project_tags", "project_id", "portfolio", "projects"))
                .isEqualTo(1);
        assertThat(PortfolioSchemaAssertions.foreignKeyCount("project_tags", "tag_id", "taxonomy", "tags"))
                .isZero();
    }

    @Test
    void assetReferencesHaveNoPhysicalMediaForeignKeys() throws SQLException {
        assertThat(PortfolioSchemaAssertions.foreignKeyCount("projects", "cover_asset_id", "media", "assets"))
                .isZero();
        assertThat(PortfolioSchemaAssertions.foreignKeyCount(
                "site_presentation_settings", "default_og_image_asset_id", "media", "assets"))
                .isZero();
    }

    @Test
    void portfolioHasNoForeignKeysToDiscoverySchema() throws SQLException {
        assertThat(PortfolioSchemaAssertions.portfolioForeignKeyCountToSchema("discovery")).isZero();
    }
}
