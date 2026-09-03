package dev.persefonia.app.webadmin.cv;

import dev.persefonia.app.audit.MvcAuditTestConfiguration;
import dev.persefonia.profileportfolio.application.port.ActiveCvAssetEligibilityPort;
import dev.persefonia.profileportfolio.application.port.EligibleCvAsset;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("admin-cv-mvc-test")
@Import(MvcAuditTestConfiguration.class)
class AdminCvTestConfiguration {
    static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");
    static final MediaAssetId PUBLIC_PDF_ID = MediaAssetId.from(UUID.fromString("00000000-0000-0000-0000-00000000c801"));
    static final MediaAssetId SECOND_PUBLIC_PDF_ID = MediaAssetId.from(UUID.fromString("00000000-0000-0000-0000-00000000c802"));

    @Bean
    @Primary
    AdminCvProfileRepositoryStub adminCvProfileRepositoryStub() {
        return new AdminCvProfileRepositoryStub();
    }

    @Bean
    @Primary
    AdminCvSettingsRepositoryStub adminCvSettingsRepositoryStub() {
        return new AdminCvSettingsRepositoryStub();
    }

    @Bean
    @Primary
    AdminCvEligibilityStub adminCvEligibilityStub() {
        return new AdminCvEligibilityStub();
    }

    static final class AdminCvProfileRepositoryStub implements ActiveCvProfileRepository {
        private ActiveCvProfile profile;
        private int saveCount;

        AdminCvProfileRepositoryStub() {
            reset();
        }

        void reset() {
            profile = ActiveCvProfile.rehydrate(
                    ActiveCvProfileId.from(UUID.fromString("00000000-0000-0000-0000-000000000801")),
                    List.of(),
                    NOW,
                    NOW,
                    Version.initial());
            saveCount = 0;
        }

        ActiveCvProfile profile() {
            return profile;
        }

        int saveCount() {
            return saveCount;
        }

        @Override
        public Optional<ActiveCvProfile> findSingleton() {
            return Optional.of(profile);
        }

        @Override
        public ActiveCvProfile save(ActiveCvProfile profile) {
            this.profile = profile;
            saveCount++;
            return profile;
        }
    }

    static final class AdminCvSettingsRepositoryStub implements SitePresentationSettingsRepository {
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

    static final class AdminCvEligibilityStub implements ActiveCvAssetEligibilityPort {
        private final List<EligibleCvAsset> candidates = new ArrayList<>();

        AdminCvEligibilityStub() {
            reset();
        }

        void reset() {
            candidates.clear();
            candidates.add(candidate(PUBLIC_PDF_ID, "cv-en.pdf"));
            candidates.add(candidate(SECOND_PUBLIC_PDF_ID, "cv-tr.pdf"));
        }

        void clear() {
            candidates.clear();
        }

        @Override
        public Optional<EligibleCvAsset> findEligiblePublicPdf(MediaAssetId assetId) {
            return candidates.stream().filter(candidate -> candidate.mediaAssetId().equals(assetId)).findFirst();
        }

        @Override
        public List<EligibleCvAsset> listEligiblePublicPdfCandidates() {
            return List.copyOf(candidates);
        }

        private static EligibleCvAsset candidate(MediaAssetId assetId, String filename) {
            return new EligibleCvAsset(assetId, filename, "application/pdf", 100, NOW);
        }
    }
}
