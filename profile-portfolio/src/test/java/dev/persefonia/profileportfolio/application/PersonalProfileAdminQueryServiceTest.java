package dev.persefonia.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.profileportfolio.application.service.PersonalProfileAdminQueryService;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.profile.DisplayName;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLink;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLinkId;
import dev.persefonia.profileportfolio.domain.profile.LocationText;
import dev.persefonia.profileportfolio.domain.profile.LongBio;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfile;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfileRepository;
import dev.persefonia.profileportfolio.domain.profile.ProfileId;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalization;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalizationId;
import dev.persefonia.profileportfolio.domain.profile.ShortBio;
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
import org.junit.jupiter.api.Test;

class PersonalProfileAdminQueryServiceTest {
    @Test
    void returnsOnboardingViewWhenNoActiveProfileExists() {
        var service = new PersonalProfileAdminQueryService(new FakeProfileRepository(null), settings());

        var view = service.current();

        assertThat(view.profileExists()).isFalse();
        assertThat(view.defaultLanguage()).isEqualTo("TR");
        assertThat(view.localizations()).isEmpty();
    }

    @Test
    void returnsEditableViewWhenActiveProfileExists() {
        var service = new PersonalProfileAdminQueryService(new FakeProfileRepository(profile()), settings());

        var view = service.current();

        assertThat(view.profileExists()).isTrue();
        assertThat(view.displayName()).isEqualTo("Enes");
        assertThat(view.localization("TR")).isPresent();
        assertThat(view.externalLinks()).extracting(link -> link.label()).containsExactly("Site");
    }

    private static PersonalProfile profile() {
        return PersonalProfile.create(
                ProfileId.newId(),
                DisplayName.of("Enes"),
                true,
                List.of(new ProfileLocalization(
                        ProfileLocalizationId.newId(),
                        ContentLanguage.TR,
                        ShortBio.of("Short"),
                        LongBio.of("Long"),
                        LocationText.of("Istanbul"),
                        List.of(),
                        List.of(),
                        List.of())),
                List.of(new ExternalProfileLink(
                        ExternalProfileLinkId.newId(),
                        LinkLabel.of("Site"),
                        ExternalUrl.of("https://example.test"),
                        SortOrder.of(1))),
                Instant.parse("2026-06-16T10:00:00Z"));
    }

    private static FakeSettingsRepository settings() {
        return new FakeSettingsRepository(SitePresentationSettings.create(
                SitePresentationSettingsId.newId(),
                SiteName.of("Persefonia"),
                ContentLanguage.TR,
                Set.of(ContentLanguage.TR, ContentLanguage.EN),
                null,
                null,
                null,
                ThemePreference.SYSTEM,
                HomepageSettings.of(true, true, false, PositiveInteger.of(3), PositiveInteger.of(5)),
                Instant.parse("2026-06-16T10:00:00Z")));
    }

    private record FakeProfileRepository(PersonalProfile current) implements PersonalProfileRepository {
        @Override
        public PersonalProfile save(PersonalProfile profile) {
            return profile;
        }

        @Override
        public Optional<PersonalProfile> findById(ProfileId id) {
            return Optional.ofNullable(current).filter(profile -> profile.id().equals(id));
        }

        @Override
        public Optional<PersonalProfile> findActiveProfile() {
            return Optional.ofNullable(current).filter(PersonalProfile::active);
        }
    }

    private record FakeSettingsRepository(SitePresentationSettings current) implements SitePresentationSettingsRepository {
        @Override
        public SitePresentationSettings save(SitePresentationSettings settings) {
            return settings;
        }

        @Override
        public Optional<SitePresentationSettings> findCurrent() {
            return Optional.ofNullable(current);
        }

        @Override
        public Optional<SitePresentationSettings> findById(SitePresentationSettingsId id) {
            return Optional.ofNullable(current).filter(settings -> settings.id().equals(id));
        }
    }
}
