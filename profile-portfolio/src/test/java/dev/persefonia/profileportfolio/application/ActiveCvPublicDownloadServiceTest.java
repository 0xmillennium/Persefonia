package dev.persefonia.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetContent;
import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetPort;
import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetReference;
import dev.persefonia.profileportfolio.application.query.ActiveCvDownload;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicDownloadService;
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
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveCvPublicDownloadServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");
    private static final MediaAssetId EN_ASSET = MediaAssetId.from(UUID.randomUUID());
    private static final MediaAssetId TR_ASSET = MediaAssetId.from(UUID.randomUUID());

    @Test
    void defaultLanguageActiveCvOpensPublicPdfContent() throws Exception {
        FakeProfileRepository profiles = profiles();
        profiles.profile.selectDocument(ContentLanguage.TR, TR_ASSET, null, NOW);

        ActiveCvDownload download = service(profiles, port(TR_ASSET)).defaultLanguageDownload().orElseThrow();

        assertThat(download.language()).isEqualTo("tr");
        assertThat(download.filename()).isEqualTo("cv-tr.pdf");
        assertThat(download.contentType()).isEqualTo("application/pdf");
        assertThat(download.contentLength()).isEqualTo(4);
        assertThat(download.inputStream().readAllBytes()).isEqualTo("%PDF".getBytes());
    }

    @Test
    void explicitLanguageActiveCvOpensPublicPdfContent() {
        FakeProfileRepository profiles = profiles();
        profiles.profile.selectDocument(ContentLanguage.EN, EN_ASSET, null, NOW);

        ActiveCvDownload download = service(profiles, port(EN_ASSET)).explicitLanguageDownload("en").orElseThrow();

        assertThat(download.language()).isEqualTo("en");
        assertThat(download.filename()).isEqualTo("cv-en.pdf");
        assertThat(download.assetUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void noSelectionReturnsEmpty() {
        assertThat(service(profiles(), port(EN_ASSET)).explicitLanguageDownload("en")).isEmpty();
    }

    @Test
    void unsupportedLanguageReturnsEmpty() {
        assertThat(service(profiles(), port(EN_ASSET)).explicitLanguageDownload("download")).isEmpty();
    }

    @Test
    void privateMissingOrIneligibleAssetReturnsEmpty() {
        FakeProfileRepository profiles = profiles();
        profiles.profile.selectDocument(ContentLanguage.EN, EN_ASSET, null, NOW);

        assertThat(service(profiles, port()).explicitLanguageDownload("en")).isEmpty();
    }

    @Test
    void missingStorageContentReturnsEmpty() {
        FakeProfileRepository profiles = profiles();
        profiles.profile.selectDocument(ContentLanguage.EN, EN_ASSET, null, NOW);

        assertThat(service(profiles, missingContentPort(EN_ASSET)).explicitLanguageDownload("en")).isEmpty();
    }

    @Test
    void downloadDoesNotExposeStoragePathAndDoesNotFallbackToAnotherLanguage() {
        FakeProfileRepository profiles = profiles();
        profiles.profile.selectDocument(ContentLanguage.EN, EN_ASSET, null, NOW);

        assertThat(service(profiles, port(EN_ASSET)).explicitLanguageDownload("tr")).isEmpty();
        assertThat(service(profiles, port(EN_ASSET)).explicitLanguageDownload("en").orElseThrow().toString())
                .doesNotContain("storage", "publicUrl", "/media/assets");
    }

    private static ActiveCvPublicDownloadService service(
            FakeProfileRepository profiles,
            ActiveCvPublicAssetPort assets) {
        return new ActiveCvPublicDownloadService(profiles, new FakeSettingsRepository(), assets);
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

    private static ActiveCvPublicAssetPort missingContentPort(MediaAssetId eligible) {
        return new ActiveCvPublicAssetPort() {
            @Override
            public Optional<ActiveCvPublicAssetReference> findPublicPdf(MediaAssetId assetId) {
                return eligible.equals(assetId)
                        ? Optional.of(new ActiveCvPublicAssetReference(assetId, "application/pdf", 100, NOW))
                        : Optional.empty();
            }

            @Override
            public Optional<ActiveCvPublicAssetContent> openPublicPdf(MediaAssetId assetId) {
                return Optional.empty();
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
