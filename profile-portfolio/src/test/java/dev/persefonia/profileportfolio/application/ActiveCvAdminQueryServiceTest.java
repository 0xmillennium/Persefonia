package dev.persefonia.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.profileportfolio.application.port.ActiveCvAssetEligibilityPort;
import dev.persefonia.profileportfolio.application.port.EligibleCvAsset;
import dev.persefonia.profileportfolio.application.service.ActiveCvAdminQueryService;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.Version;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfile;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileId;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileRepository;
import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import dev.persefonia.profileportfolio.domain.settings.HomepageSettings;
import dev.persefonia.profileportfolio.domain.settings.PositiveInteger;
import dev.persefonia.profileportfolio.domain.settings.SiteName;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsId;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveCvAdminQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");
    private static final MediaAssetId ASSET_ID = MediaAssetId.from(UUID.randomUUID());

    @Test
    void returnsSupportedLanguagesCurrentSelectionsAndCandidates() {
        FakeProfileRepository profiles = new FakeProfileRepository();
        profiles.profile.selectDocument(ContentLanguage.EN, ASSET_ID, null, NOW);
        var service = new ActiveCvAdminQueryService(profiles, new FakeSettingsRepository(), eligibility(ASSET_ID));

        var data = service.pageData();

        assertThat(data.supportedLanguages()).containsExactly("EN", "TR");
        assertThat(data.selections()).filteredOn(selection -> "EN".equals(selection.language()))
                .singleElement()
                .satisfies(selection -> assertThat(selection.mediaAssetId()).isEqualTo(ASSET_ID.value()));
        assertThat(data.candidates()).singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.mediaAssetId()).isEqualTo(ASSET_ID.value());
                    assertThat(candidate.contentType()).isEqualTo("application/pdf");
                });
    }

    @Test
    void returnsEmptyCandidateListSafely() {
        var service = new ActiveCvAdminQueryService(
                new FakeProfileRepository(), new FakeSettingsRepository(), eligibility());

        assertThat(service.pageData().candidates()).isEmpty();
    }

    private static ActiveCvAssetEligibilityPort eligibility(MediaAssetId... assetIds) {
        List<EligibleCvAsset> assets = Set.of(assetIds).stream()
                .map(assetId -> new EligibleCvAsset(assetId, "cv.pdf", "application/pdf", 100, NOW))
                .toList();
        return new ActiveCvAssetEligibilityPort() {
            @Override
            public Optional<EligibleCvAsset> findEligiblePublicPdf(MediaAssetId assetId) {
                return assets.stream().filter(asset -> asset.mediaAssetId().equals(assetId)).findFirst();
            }

            @Override
            public List<EligibleCvAsset> listEligiblePublicPdfCandidates() {
                return assets;
            }
        };
    }

    private static final class FakeProfileRepository implements ActiveCvProfileRepository {
        private final ActiveCvProfile profile = ActiveCvProfile.rehydrate(
                ActiveCvProfileId.from(UUID.randomUUID()), List.of(), NOW, NOW, Version.initial());

        @Override
        public Optional<ActiveCvProfile> findSingleton() {
            return Optional.of(profile);
        }

        @Override
        public ActiveCvProfile save(ActiveCvProfile profile) {
            return profile;
        }
    }

    private static final class FakeSettingsRepository implements SitePresentationSettingsRepository {
        @Override
        public SitePresentationSettings save(SitePresentationSettings settings) {
            return settings;
        }

        @Override
        public Optional<SitePresentationSettings> findCurrent() {
            return Optional.of(SitePresentationSettings.create(
                    SitePresentationSettingsId.newId(),
                    SiteName.of("Persefonia"),
                    ContentLanguage.TR,
                    Set.of(ContentLanguage.TR, ContentLanguage.EN),
                    null,
                    null,
                    null,
                    ThemePreference.SYSTEM,
                    HomepageSettings.of(true, true, false, PositiveInteger.of(3), PositiveInteger.of(5)),
                    NOW));
        }

        @Override
        public Optional<SitePresentationSettings> findById(SitePresentationSettingsId id) {
            return findCurrent();
        }
    }
}
