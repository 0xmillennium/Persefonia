package dev.persefonia.app.webpublic.cv;

import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetContent;
import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetPort;
import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetReference;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("public-cv-mvc-test")
class PublicCvTestConfiguration {
    static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");
    static final MediaAssetId EN_PDF_ID = MediaAssetId.from(UUID.fromString("00000000-0000-0000-0000-00000000c861"));
    static final MediaAssetId TR_PDF_ID = MediaAssetId.from(UUID.fromString("00000000-0000-0000-0000-00000000c862"));

    @Bean
    @Primary
    PublicCvProfileRepositoryStub publicCvProfileRepositoryStub() {
        return new PublicCvProfileRepositoryStub();
    }

    @Bean
    @Primary
    PublicCvSettingsRepositoryStub publicCvSettingsRepositoryStub() {
        return new PublicCvSettingsRepositoryStub();
    }

    @Bean
    @Primary
    PublicCvAssetPortStub publicCvAssetPortStub() {
        return new PublicCvAssetPortStub();
    }

    static final class PublicCvProfileRepositoryStub implements ActiveCvProfileRepository {
        private ActiveCvProfile profile;

        PublicCvProfileRepositoryStub() {
            reset();
        }

        void reset() {
            profile = ActiveCvProfile.rehydrate(
                    ActiveCvProfileId.from(UUID.fromString("00000000-0000-0000-0000-000000000861")),
                    List.of(),
                    NOW,
                    NOW,
                    Version.initial());
        }

        ActiveCvProfile profile() {
            return profile;
        }

        @Override
        public Optional<ActiveCvProfile> findSingleton() {
            return Optional.of(profile);
        }

        @Override
        public ActiveCvProfile save(ActiveCvProfile profile) {
            this.profile = profile;
            return profile;
        }
    }

    static final class PublicCvSettingsRepositoryStub implements SitePresentationSettingsRepository {
        @Override
        public SitePresentationSettings save(SitePresentationSettings settings) {
            return settings;
        }

        @Override
        public Optional<SitePresentationSettings> findCurrent() {
            return Optional.of(SitePresentationSettings.create(
                    SitePresentationSettingsId.newId(),
                    SiteName.of("Persefonia"),
                    ContentLanguage.EN,
                    Set.of(ContentLanguage.EN, ContentLanguage.TR),
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

    static final class PublicCvAssetPortStub implements ActiveCvPublicAssetPort {
        private final Map<MediaAssetId, AssetState> assets = new LinkedHashMap<>();

        PublicCvAssetPortStub() {
            reset();
        }

        void reset() {
            assets.clear();
            assets.put(EN_PDF_ID, new AssetState(true, true));
            assets.put(TR_PDF_ID, new AssetState(true, true));
        }

        void makePrivate(MediaAssetId assetId) {
            assets.put(assetId, new AssetState(false, true));
        }

        void removeContent(MediaAssetId assetId) {
            assets.put(assetId, new AssetState(true, false));
        }

        @Override
        public Optional<ActiveCvPublicAssetReference> findPublicPdf(MediaAssetId assetId) {
            AssetState state = assets.get(assetId);
            if (state == null || !state.publicPdf()) {
                return Optional.empty();
            }
            return Optional.of(new ActiveCvPublicAssetReference(assetId, "application/pdf", bytes(assetId).length, NOW));
        }

        @Override
        public Optional<ActiveCvPublicAssetContent> openPublicPdf(MediaAssetId assetId) {
            AssetState state = assets.get(assetId);
            byte[] bytes = bytes(assetId);
            if (state == null || !state.publicPdf() || !state.contentAvailable()) {
                return Optional.empty();
            }
            return Optional.of(new ActiveCvPublicAssetContent(
                    new ByteArrayInputStream(bytes),
                    "application/pdf",
                    bytes.length,
                    NOW));
        }

        private static byte[] bytes(MediaAssetId assetId) {
            return ("%PDF-" + assetId.value()).getBytes();
        }
    }

    private record AssetState(boolean publicPdf, boolean contentAvailable) {
    }
}
