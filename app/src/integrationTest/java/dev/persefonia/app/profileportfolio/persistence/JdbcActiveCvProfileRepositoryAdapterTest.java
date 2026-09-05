package dev.persefonia.app.profileportfolio.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfile;
import dev.persefonia.profileportfolio.domain.cv.CvDisplayLabel;
import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class JdbcActiveCvProfileRepositoryAdapterTest extends PortfolioRepositoryTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    @Test
    void loadsSeededSingleton() {
        ActiveCvProfile profile = activeCvProfiles.findSingleton().orElseThrow();

        assertThat(profile.id().value()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000801"));
        assertThat(profile.documents()).isEmpty();
    }

    @Test
    void savesSelections() {
        ActiveCvProfile profile = activeCvProfiles.findSingleton().orElseThrow();
        MediaAssetId assetId = MediaAssetId.from(UUID.randomUUID());
        profile.selectDocument(ContentLanguage.EN, assetId, CvDisplayLabel.of("English CV"), NOW);

        ActiveCvProfile saved = activeCvProfiles.save(profile);

        assertThat(saved.documentFor(ContentLanguage.EN)).isPresent();
        assertThat(saved.documentFor(ContentLanguage.EN).orElseThrow().mediaAssetId()).isEqualTo(assetId);
        assertThat(saved.version().value()).isEqualTo(1);
    }

    @Test
    void replacesSelections() {
        ActiveCvProfile profile = activeCvProfiles.findSingleton().orElseThrow();
        profile.selectDocument(ContentLanguage.EN, MediaAssetId.from(UUID.randomUUID()), null, NOW);
        ActiveCvProfile saved = activeCvProfiles.save(profile);
        MediaAssetId replacement = MediaAssetId.from(UUID.randomUUID());
        saved.selectDocument(ContentLanguage.EN, replacement, null, NOW.plusSeconds(1));

        ActiveCvProfile reloaded = activeCvProfiles.save(saved);

        assertThat(reloaded.documents()).hasSize(1);
        assertThat(reloaded.documentFor(ContentLanguage.EN).orElseThrow().mediaAssetId()).isEqualTo(replacement);
    }

    @Test
    void clearsSelection() {
        ActiveCvProfile profile = activeCvProfiles.findSingleton().orElseThrow();
        profile.selectDocument(ContentLanguage.EN, MediaAssetId.from(UUID.randomUUID()), null, NOW);
        ActiveCvProfile saved = activeCvProfiles.save(profile);
        saved.removeDocument(ContentLanguage.EN, NOW.plusSeconds(1));

        ActiveCvProfile reloaded = activeCvProfiles.save(saved);

        assertThat(reloaded.documentFor(ContentLanguage.EN)).isEmpty();
    }

    @Test
    void optimisticVersionConflictFails() {
        ActiveCvProfile first = activeCvProfiles.findSingleton().orElseThrow();
        ActiveCvProfile second = activeCvProfiles.findSingleton().orElseThrow();
        first.selectDocument(ContentLanguage.EN, MediaAssetId.from(UUID.randomUUID()), null, NOW);
        activeCvProfiles.save(first);
        second.selectDocument(ContentLanguage.TR, MediaAssetId.from(UUID.randomUUID()), null, NOW);

        assertThatThrownBy(() -> activeCvProfiles.save(second))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void doesNotRequireMediaForeignKey() {
        ActiveCvProfile profile = activeCvProfiles.findSingleton().orElseThrow();
        MediaAssetId nonexistentMediaAssetId = MediaAssetId.from(UUID.randomUUID());
        profile.selectDocument(ContentLanguage.EN, nonexistentMediaAssetId, null, NOW);

        ActiveCvProfile saved = activeCvProfiles.save(profile);

        assertThat(saved.documentFor(ContentLanguage.EN).orElseThrow().mediaAssetId()).isEqualTo(nonexistentMediaAssetId);
    }
}
