package dev.persefonia.app.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.persefonia.app.audit.integration.ProfilePortfolioAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.command.ActiveCvSelectionInput;
import dev.persefonia.profileportfolio.application.command.UpdateActiveCvCommand;
import dev.persefonia.profileportfolio.application.port.ActiveCvAssetEligibilityPort;
import dev.persefonia.profileportfolio.application.port.EligibleCvAsset;
import dev.persefonia.profileportfolio.application.service.ActiveCvCommandService;
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
import org.springframework.transaction.annotation.Transactional;

class TransactionalActiveCvCommandGatewayTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");
    private static final MediaAssetId ASSET_ID = MediaAssetId.from(UUID.randomUUID());

    @Test
    void delegatesUpdateCommandToService() {
        FakeProfileRepository profiles = new FakeProfileRepository();
        var gateway = new TransactionalActiveCvCommandGateway(new ActiveCvCommandService(
                profiles,
                new FakeSettingsRepository(),
                eligibility(),
                allowOwner()),
                mock(AppendAuditRecordPort.class),
                mock(ProfilePortfolioAuditMapper.class));

        var result = gateway.update(new UpdateActiveCvCommand(
                new PortfolioCommandActor(UUID.randomUUID(), true, true),
                List.of(new ActiveCvSelectionInput("EN", ASSET_ID.value().toString(), "")),
                NOW));

        assertThat(result.updated()).isTrue();
        assertThat(profiles.saveCount).isEqualTo(1);
    }

    @Test
    void updateMethodIsTransactional() throws NoSuchMethodException {
        assertThat(TransactionalActiveCvCommandGateway.class
                .getMethod("update", UpdateActiveCvCommand.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static ActiveCvAssetEligibilityPort eligibility() {
        return new ActiveCvAssetEligibilityPort() {
            @Override
            public Optional<EligibleCvAsset> findEligiblePublicPdf(MediaAssetId assetId) {
                return ASSET_ID.equals(assetId)
                        ? Optional.of(new EligibleCvAsset(assetId, "cv.pdf", "application/pdf", 100, NOW))
                        : Optional.empty();
            }

            @Override
            public List<EligibleCvAsset> listEligiblePublicPdfCandidates() {
                return List.of(new EligibleCvAsset(ASSET_ID, "cv.pdf", "application/pdf", 100, NOW));
            }
        };
    }

    private static PortfolioCommandAuthorizationPolicy allowOwner() {
        return (actor, commandName) -> {
            if (!actor.owner()) {
                throw new SecurityException(commandName);
            }
        };
    }

    private static final class FakeProfileRepository implements ActiveCvProfileRepository {
        private ActiveCvProfile profile = ActiveCvProfile.rehydrate(
                ActiveCvProfileId.from(UUID.randomUUID()), List.of(), NOW, NOW, Version.initial());
        private int saveCount;

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
