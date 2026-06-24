package dev.persefonia.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetContent;
import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetPort;
import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetReference;
import dev.persefonia.profileportfolio.application.query.ActiveCvPublicView;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicQueryService;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.Version;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfile;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileId;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileRepository;
import dev.persefonia.profileportfolio.domain.cv.CvDisplayLabel;
import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import dev.persefonia.profileportfolio.domain.settings.HomepageSettings;
import dev.persefonia.profileportfolio.domain.settings.PositiveInteger;
import dev.persefonia.profileportfolio.domain.settings.SiteName;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsId;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveCvPublicQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");
    private static final MediaAssetId EN_ASSET = MediaAssetId.from(UUID.randomUUID());
    private static final MediaAssetId TR_ASSET = MediaAssetId.from(UUID.randomUUID());

    @Test
    void defaultLanguageActiveCvReturnsPublicView() {
        FakeProfileRepository profiles = profiles();
        profiles.profile.selectDocument(ContentLanguage.TR, TR_ASSET, null, NOW);

        ActiveCvPublicView view = service(profiles, port(TR_ASSET)).defaultLanguageView().orElseThrow();

        assertThat(view.language()).isEqualTo("tr");
        assertThat(view.displayLabel()).isEqualTo("CV");
        assertThat(view.downloadPath()).isEqualTo("/cv/tr/download");
        assertThat(view.displayFilename()).isEqualTo("cv-tr.pdf");
        assertThat(view.contentType()).isEqualTo("application/pdf");
        assertThat(view.sizeBytes()).isEqualTo(100);
    }

    @Test
    void explicitSupportedLanguageActiveCvReturnsPublicView() {
        FakeProfileRepository profiles = profiles();
        profiles.profile.selectDocument(ContentLanguage.EN, EN_ASSET, CvDisplayLabel.of("Academic CV"), NOW);

        ActiveCvPublicView view = service(profiles, port(EN_ASSET)).explicitLanguageView("en").orElseThrow();

        assertThat(view.language()).isEqualTo("en");
        assertThat(view.displayLabel()).isEqualTo("Academic CV");
        assertThat(view.downloadPath()).isEqualTo("/cv/en/download");
        assertThat(view.selectedAt()).isEqualTo(NOW);
        assertThat(view.assetUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void supportedLanguageWithNoSelectionReturnsEmpty() {
        assertThat(service(profiles(), port(EN_ASSET)).explicitLanguageView("en")).isEmpty();
    }

    @Test
    void unsupportedLanguageReturnsEmpty() {
        assertThat(service(profiles(), port(EN_ASSET)).explicitLanguageView("de")).isEmpty();
    }

    @Test
    void selectionWhoseAssetIsNoLongerPublicPdfReturnsEmpty() {
        FakeProfileRepository profiles = profiles();
        profiles.profile.selectDocument(ContentLanguage.EN, EN_ASSET, null, NOW);

        assertThat(service(profiles, port()).explicitLanguageView("en")).isEmpty();
    }

    @Test
    void viewDoesNotExposeStoragePathAndDoesNotFallbackToAnotherLanguage() {
        FakeProfileRepository profiles = profiles();
        profiles.profile.selectDocument(ContentLanguage.EN, EN_ASSET, null, NOW);

        assertThat(service(profiles, port(EN_ASSET)).explicitLanguageView("tr")).isEmpty();
        assertThat(service(profiles, port(EN_ASSET)).explicitLanguageView("en").orElseThrow().toString())
                .doesNotContain("storage", "publicUrl", "/media/assets");
    }

    private static ActiveCvPublicQueryService service(
            FakeProfileRepository profiles,
            ActiveCvPublicAssetPort assets) {
        return new ActiveCvPublicQueryService(profiles, new FakeSettingsRepository(), assets);
    }

    private static FakeProfileRepository profiles() {
        return new FakeProfileRepository();
    }

    private static ActiveCvPublicAssetPort port(MediaAssetId... eligible) {
        Set<MediaAssetId> eligibleAssets = Set.of(eligible);
        return new ActiveCvPublicAssetPort() {
            @Override
            public Optional<ActiveCvPublicAssetReference> findPublicPdf(MediaAssetId assetId) {
                return eligibleAssets.contains(assetId)
                        ? Optional.of(new ActiveCvPublicAssetReference(assetId, "application/pdf", 100, NOW))
                        : Optional.empty();
            }

            @Override
            public Optional<ActiveCvPublicAssetContent> openPublicPdf(MediaAssetId assetId) {
                return eligibleAssets.contains(assetId)
                        ? Optional.of(new ActiveCvPublicAssetContent(
                                new ByteArrayInputStream("%PDF".getBytes()), "application/pdf", 4, NOW))
                        : Optional.empty();
            }
        };
    }

    private static final class FakeProfileRepository implements ActiveCvProfileRepository {
        private final ActiveCvProfile profile = ActiveCvProfile.rehydrate(
                ActiveCvProfileId.from(UUID.randomUUID()), List.of(), NOW, NOW, Version.initial());

        @Override public Optional<ActiveCvProfile> findSingleton() {
            return Optional.of(profile);
        }
        @Override public ActiveCvProfile save(ActiveCvProfile profile) {
            return profile;
        }
    }

    private static final class FakeSettingsRepository implements SitePresentationSettingsRepository {
        @Override public SitePresentationSettings save(SitePresentationSettings settings) {
            return settings;
        }
        @Override public Optional<SitePresentationSettings> findCurrent() {
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
        @Override public Optional<SitePresentationSettings> findById(SitePresentationSettingsId id) {
            return findCurrent();
        }
    }
}
