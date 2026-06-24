package dev.persefonia.app.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveCvPublicApplicationConfigurationTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    @Test
    void createsPublicQueryAndDownloadServicesFromPorts() {
        ActiveCvPublicApplicationConfiguration configuration = new ActiveCvPublicApplicationConfiguration();
        ActiveCvProfileRepository profiles = new FakeProfileRepository();
        SitePresentationSettingsRepository settings = new FakeSettingsRepository();
        ActiveCvPublicAssetPort assets = new FakeAssetPort();

        assertThat(configuration.activeCvPublicQueryService(profiles, settings, assets)).isNotNull();
        assertThat(configuration.activeCvPublicDownloadService(profiles, settings, assets)).isNotNull();
    }

    private static final class FakeProfileRepository implements ActiveCvProfileRepository {
        @Override public Optional<ActiveCvProfile> findSingleton() {
            return Optional.of(ActiveCvProfile.rehydrate(
                    ActiveCvProfileId.from(UUID.randomUUID()), List.of(), NOW, NOW, Version.initial()));
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
                    ContentLanguage.EN,
                    Set.of(ContentLanguage.EN, ContentLanguage.TR),
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

    private static final class FakeAssetPort implements ActiveCvPublicAssetPort {
        @Override public Optional<ActiveCvPublicAssetReference> findPublicPdf(MediaAssetId assetId) {
            return Optional.of(new ActiveCvPublicAssetReference(assetId, "application/pdf", 4, NOW));
        }
        @Override public Optional<ActiveCvPublicAssetContent> openPublicPdf(MediaAssetId assetId) {
            return Optional.of(new ActiveCvPublicAssetContent(
                    new ByteArrayInputStream("%PDF".getBytes()), "application/pdf", 4, NOW));
        }
    }
}
