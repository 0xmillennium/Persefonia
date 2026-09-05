package dev.persefonia.app.profileportfolio.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfile;
import dev.persefonia.profileportfolio.domain.cv.CvDisplayLabel;
import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class JdbcActiveCvProfileRepositoryAdapterTest extends PortfolioRepositoryTestDatabase {
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
        profile.selectDocument(ContentLanguage.EN, assetId, CvDisplayLabel.of("English CV"), after(profile));

        ActiveCvProfile saved = activeCvProfiles.save(profile);

        assertThat(saved.documentFor(ContentLanguage.EN)).isPresent();
        assertThat(saved.documentFor(ContentLanguage.EN).orElseThrow().mediaAssetId()).isEqualTo(assetId);
        assertThat(saved.version().value()).isEqualTo(1);
    }

    @Test
    void replacesSelections() {
        ActiveCvProfile profile = activeCvProfiles.findSingleton().orElseThrow();
        profile.selectDocument(ContentLanguage.EN, MediaAssetId.from(UUID.randomUUID()), null, after(profile));
        ActiveCvProfile saved = activeCvProfiles.save(profile);
        MediaAssetId replacement = MediaAssetId.from(UUID.randomUUID());
        saved.selectDocument(ContentLanguage.EN, replacement, null, after(saved));

        ActiveCvProfile reloaded = activeCvProfiles.save(saved);

        assertThat(reloaded.documents()).hasSize(1);
        assertThat(reloaded.documentFor(ContentLanguage.EN).orElseThrow().mediaAssetId()).isEqualTo(replacement);
    }

    @Test
    void clearsSelection() {
        ActiveCvProfile profile = activeCvProfiles.findSingleton().orElseThrow();
        profile.selectDocument(ContentLanguage.EN, MediaAssetId.from(UUID.randomUUID()), null, after(profile));
        ActiveCvProfile saved = activeCvProfiles.save(profile);
        saved.removeDocument(ContentLanguage.EN, after(saved));

        ActiveCvProfile reloaded = activeCvProfiles.save(saved);

        assertThat(reloaded.documentFor(ContentLanguage.EN)).isEmpty();
    }

    @Test
    void optimisticVersionConflictFails() {
        ActiveCvProfile first = activeCvProfiles.findSingleton().orElseThrow();
        ActiveCvProfile second = activeCvProfiles.findSingleton().orElseThrow();
        first.selectDocument(ContentLanguage.EN, MediaAssetId.from(UUID.randomUUID()), null, after(first));
        activeCvProfiles.save(first);
        second.selectDocument(ContentLanguage.TR, MediaAssetId.from(UUID.randomUUID()), null, after(second));

        assertThatThrownBy(() -> activeCvProfiles.save(second))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void doesNotRequireMediaForeignKey() {
        ActiveCvProfile profile = activeCvProfiles.findSingleton().orElseThrow();
        MediaAssetId nonexistentMediaAssetId = MediaAssetId.from(UUID.randomUUID());
        profile.selectDocument(ContentLanguage.EN, nonexistentMediaAssetId, null, after(profile));

        ActiveCvProfile saved = activeCvProfiles.save(profile);

        assertThat(saved.documentFor(ContentLanguage.EN).orElseThrow().mediaAssetId()).isEqualTo(nonexistentMediaAssetId);
    }

    private static java.time.Instant after(ActiveCvProfile profile) {
        return profile.updatedAt().plusSeconds(1);
    }
}
