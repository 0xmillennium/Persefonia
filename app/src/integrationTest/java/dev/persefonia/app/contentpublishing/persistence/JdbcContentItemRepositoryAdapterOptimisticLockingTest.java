package dev.persefonia.app.contentpublishing.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.content.Title;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class JdbcContentItemRepositoryAdapterOptimisticLockingTest extends ContentPublishingRepositoryTestDatabase {
    @Test
    void staleLoadedContentItemSaveFails() {
        var saved = contentItems.save(ContentItemRepositoryTestFixtures.completeDraft("locking"));
        var firstCopy = contentItems.findById(saved.id()).orElseThrow();
        var staleCopy = contentItems.findById(saved.id()).orElseThrow();

        firstCopy.changeTitle(Title.of("First update"), ContentItemRepositoryTestFixtures.NOW.plusSeconds(40));
        var updated = contentItems.save(firstCopy);

        staleCopy.changeTitle(Title.of("Stale update"), ContentItemRepositoryTestFixtures.NOW.plusSeconds(41));
        assertThat(updated.version().value()).isEqualTo(1L);
        assertThatThrownBy(() -> contentItems.save(staleCopy))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
