package dev.persefonia.app.contentpublishing.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentPublishingRepositoryContextTest extends ContentPublishingRepositoryTestDatabase {
    @Test
    void usesRealJdbcContentRepositories() {
        assertThat(contentItems).isInstanceOf(JdbcContentItemRepositoryAdapter.class);
        assertThat(contentRevisions).isInstanceOf(JdbcContentRevisionRepositoryAdapter.class);
        assertThat(contentItems.getClass().getName()).doesNotContain("AdminContentTestRepository");
    }
}
