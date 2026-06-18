package dev.persefonia.profileportfolio.domain.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import dev.persefonia.profileportfolio.domain.common.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveCvProfileTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");
    private static final ActiveCvProfileId PROFILE_ID = ActiveCvProfileId.from(UUID.randomUUID());
    private static final MediaAssetId ASSET_ID = MediaAssetId.from(UUID.randomUUID());

    @Test
    void selectsCvForLanguage() {
        ActiveCvProfile profile = emptyProfile();

        profile.selectDocument(ContentLanguage.EN, ASSET_ID, CvDisplayLabel.of("English CV"), NOW);

        assertThat(profile.documentFor(ContentLanguage.EN)).isPresent();
        assertThat(profile.documentFor(ContentLanguage.EN).orElseThrow().mediaAssetId()).isEqualTo(ASSET_ID);
    }

    @Test
    void reselectingSameLanguageReplacesAsset() {
        ActiveCvProfile profile = emptyProfile();
        MediaAssetId replacement = MediaAssetId.from(UUID.randomUUID());

        profile.selectDocument(ContentLanguage.EN, ASSET_ID, null, NOW);
        profile.selectDocument(ContentLanguage.EN, replacement, null, NOW.plusSeconds(1));

        assertThat(profile.documents()).hasSize(1);
        assertThat(profile.documentFor(ContentLanguage.EN).orElseThrow().mediaAssetId()).isEqualTo(replacement);
    }

    @Test
    void supportsMultipleLanguages() {
        ActiveCvProfile profile = emptyProfile();

        profile.selectDocument(ContentLanguage.EN, ASSET_ID, null, NOW);
        profile.selectDocument(ContentLanguage.TR, MediaAssetId.from(UUID.randomUUID()), null, NOW);

        assertThat(profile.documents()).hasSize(2);
    }

    @Test
    void clearingLanguageRemovesSelection() {
        ActiveCvProfile profile = emptyProfile();
        profile.selectDocument(ContentLanguage.EN, ASSET_ID, null, NOW);

        profile.removeDocument(ContentLanguage.EN, NOW.plusSeconds(1));

        assertThat(profile.documentFor(ContentLanguage.EN)).isEmpty();
    }

    @Test
    void duplicateLanguageEntriesAreRejectedOnRehydrate() {
        ActiveCvDocument first = ActiveCvDocument.select(ContentLanguage.EN, ASSET_ID, null, NOW);
        ActiveCvDocument duplicate = ActiveCvDocument.select(ContentLanguage.EN, MediaAssetId.from(UUID.randomUUID()), null, NOW);

        assertThatThrownBy(() -> ActiveCvProfile.rehydrate(
                PROFILE_ID, List.of(first, duplicate), NOW, NOW, Version.initial()))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void blankDisplayLabelIsRejected() {
        assertThatThrownBy(() -> CvDisplayLabel.of(" "))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void aggregateExposesOnlyMediaAssetIdReferenceForDocument() {
        ActiveCvProfile profile = emptyProfile();

        profile.selectDocument(ContentLanguage.EN, ASSET_ID, null, NOW);

        assertThat(profile.documentFor(ContentLanguage.EN).orElseThrow().mediaAssetId().value())
                .isEqualTo(ASSET_ID.value());
    }

    private static ActiveCvProfile emptyProfile() {
        return ActiveCvProfile.rehydrate(PROFILE_ID, List.of(), NOW, NOW, Version.initial());
    }
}
