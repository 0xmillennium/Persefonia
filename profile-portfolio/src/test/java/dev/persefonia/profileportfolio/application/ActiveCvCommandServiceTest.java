package dev.persefonia.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class ActiveCvCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");
    private static final PortfolioCommandActor OWNER = new PortfolioCommandActor(UUID.randomUUID(), true, true);
    private static final PortfolioCommandActor EDITOR = new PortfolioCommandActor(UUID.randomUUID(), true, false);
    private static final MediaAssetId PUBLIC_PDF = MediaAssetId.from(UUID.randomUUID());
    private static final MediaAssetId OTHER_PUBLIC_PDF = MediaAssetId.from(UUID.randomUUID());

    @Test
    void ownerSelectsEligiblePublicPdfForSupportedLanguage() {
        FakeProfileRepository profiles = new FakeProfileRepository();
        ActiveCvCommandService service = service(profiles, eligibility(PUBLIC_PDF));

        var result = service.update(command(OWNER, new ActiveCvSelectionInput("EN", PUBLIC_PDF.value().toString(), "CV")));

        assertThat(result.updated()).isTrue();
        assertThat(profiles.profile.documentFor(ContentLanguage.EN).orElseThrow().mediaAssetId()).isEqualTo(PUBLIC_PDF);
    }

    @Test
    void ownerCanSelectDifferentPdfsPerLanguage() {
        FakeProfileRepository profiles = new FakeProfileRepository();
        ActiveCvCommandService service = service(profiles, eligibility(PUBLIC_PDF, OTHER_PUBLIC_PDF));

        var result = service.update(new UpdateActiveCvCommand(
                OWNER,
                List.of(
                        new ActiveCvSelectionInput("EN", PUBLIC_PDF.value().toString(), ""),
                        new ActiveCvSelectionInput("TR", OTHER_PUBLIC_PDF.value().toString(), "")),
                NOW));

        assertThat(result.updated()).isTrue();
        assertThat(profiles.profile.documents()).hasSize(2);
    }

    @Test
    void ownerCanClearLanguageSelection() {
        FakeProfileRepository profiles = new FakeProfileRepository();
        ActiveCvCommandService service = service(profiles, eligibility(PUBLIC_PDF));
        service.update(command(OWNER, new ActiveCvSelectionInput("EN", PUBLIC_PDF.value().toString(), "")));

        var result = service.update(command(OWNER, new ActiveCvSelectionInput("EN", "", "")));

        assertThat(result.updated()).isTrue();
        assertThat(profiles.profile.documentFor(ContentLanguage.EN)).isEmpty();
    }

    @Test
    void nonOwnerIsRejected() {
        ActiveCvCommandService service = service(new FakeProfileRepository(), eligibility(PUBLIC_PDF));

        assertThatThrownBy(() -> service.update(command(EDITOR, new ActiveCvSelectionInput("EN", PUBLIC_PDF.value().toString(), ""))))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void unsupportedLanguageIsRejected() {
        ActiveCvCommandService service = service(new FakeProfileRepository(), eligibility(PUBLIC_PDF));

        var result = service.update(command(OWNER, new ActiveCvSelectionInput("DE", PUBLIC_PDF.value().toString(), "")));

        assertThat(result.updated()).isFalse();
        assertThat(result.errors()).extracting("message").contains("Unsupported language.");
    }

    @Test
    void duplicateLanguageIsRejected() {
        ActiveCvCommandService service = service(new FakeProfileRepository(), eligibility(PUBLIC_PDF));

        var result = service.update(new UpdateActiveCvCommand(
                OWNER,
                List.of(
                        new ActiveCvSelectionInput("EN", PUBLIC_PDF.value().toString(), ""),
                        new ActiveCvSelectionInput("EN", PUBLIC_PDF.value().toString(), "")),
                NOW));

        assertThat(result.updated()).isFalse();
        assertThat(result.errors()).extracting("message").contains("Duplicate language.");
    }

    @Test
    void missingOrIneligibleAssetIsRejected() {
        ActiveCvCommandService service = service(new FakeProfileRepository(), eligibility());

        var result = service.update(command(OWNER, new ActiveCvSelectionInput("EN", PUBLIC_PDF.value().toString(), "")));

        assertThat(result.updated()).isFalse();
        assertThat(result.errors()).extracting("message").contains("Select a public PDF asset.");
    }

    @Test
    void invalidAssetIdIsRejected() {
        ActiveCvCommandService service = service(new FakeProfileRepository(), eligibility(PUBLIC_PDF));

        var result = service.update(command(OWNER, new ActiveCvSelectionInput("EN", "not-a-uuid", "")));

        assertThat(result.updated()).isFalse();
        assertThat(result.errors()).extracting("message").contains("Asset id must be a valid UUID.");
    }

    @Test
    void blankDisplayLabelIsRejectedWhenNonEmptyAssetIsSubmitted() {
        ActiveCvCommandService service = service(new FakeProfileRepository(), eligibility(PUBLIC_PDF));

        var result = service.update(command(OWNER, new ActiveCvSelectionInput("EN", PUBLIC_PDF.value().toString(), "\n")));

        assertThat(result.updated()).isFalse();
        assertThat(result.errors()).extracting("message")
                .contains("Display label must be nonblank and at most 160 characters.");
    }

    private static UpdateActiveCvCommand command(PortfolioCommandActor actor, ActiveCvSelectionInput selection) {
        return new UpdateActiveCvCommand(actor, List.of(selection), NOW);
    }

    private static ActiveCvCommandService service(
            FakeProfileRepository profiles,
            ActiveCvAssetEligibilityPort eligibility) {
        return new ActiveCvCommandService(
                profiles,
                new FakeSettingsRepository(),
                eligibility,
                new TestAuthorizationPolicy());
    }

    private static ActiveCvAssetEligibilityPort eligibility(MediaAssetId... eligibleIds) {
        Set<MediaAssetId> eligible = Set.of(eligibleIds);
        return new ActiveCvAssetEligibilityPort() {
            @Override
            public Optional<EligibleCvAsset> findEligiblePublicPdf(MediaAssetId assetId) {
                return eligible.contains(assetId)
                        ? Optional.of(new EligibleCvAsset(assetId, "cv.pdf", "application/pdf", 100, NOW))
                        : Optional.empty();
            }

            @Override
            public List<EligibleCvAsset> listEligiblePublicPdfCandidates() {
                return eligible.stream()
                        .map(assetId -> new EligibleCvAsset(assetId, "cv.pdf", "application/pdf", 100, NOW))
                        .toList();
            }
        };
    }

    private static final class TestAuthorizationPolicy implements PortfolioCommandAuthorizationPolicy {
        @Override
        public void requireOwner(PortfolioCommandActor actor, String commandName) {
            if (!actor.active() || !actor.owner()) {
                throw new SecurityException(commandName);
            }
        }
    }

    private static final class FakeProfileRepository implements ActiveCvProfileRepository {
        private ActiveCvProfile profile = ActiveCvProfile.rehydrate(
                ActiveCvProfileId.from(UUID.randomUUID()), List.of(), NOW, NOW, Version.initial());

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
