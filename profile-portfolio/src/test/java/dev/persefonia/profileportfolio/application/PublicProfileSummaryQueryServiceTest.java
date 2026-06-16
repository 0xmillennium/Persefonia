package dev.persefonia.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.profileportfolio.application.service.PublicProfileSummaryQueryService;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.profile.CurrentFocusItem;
import dev.persefonia.profileportfolio.domain.profile.CurrentFocusItemId;
import dev.persefonia.profileportfolio.domain.profile.DisplayName;
import dev.persefonia.profileportfolio.domain.profile.EducationSummary;
import dev.persefonia.profileportfolio.domain.profile.EducationSummaryId;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLink;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLinkId;
import dev.persefonia.profileportfolio.domain.profile.FocusAreaName;
import dev.persefonia.profileportfolio.domain.profile.FocusItemText;
import dev.persefonia.profileportfolio.domain.profile.InstitutionName;
import dev.persefonia.profileportfolio.domain.profile.LocationText;
import dev.persefonia.profileportfolio.domain.profile.LongBio;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfile;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfileRepository;
import dev.persefonia.profileportfolio.domain.profile.ProfileId;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalization;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalizationId;
import dev.persefonia.profileportfolio.domain.profile.ProgramName;
import dev.persefonia.profileportfolio.domain.profile.ShortBio;
import dev.persefonia.profileportfolio.domain.profile.TechnicalFocusArea;
import dev.persefonia.profileportfolio.domain.profile.TechnicalFocusAreaId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PublicProfileSummaryQueryServiceTest {
    @Test
    void noActiveProfileReturnsEmptySummary() {
        var service = new PublicProfileSummaryQueryService(new FakeProfileRepository(null));

        assertThat(service.currentSummary(ContentLanguage.TR)).isEmpty();
    }

    @Test
    void activeProfileWithoutRequestedLocalizationReturnsEmptySummary() {
        var service = new PublicProfileSummaryQueryService(new FakeProfileRepository(profile(ContentLanguage.TR)));

        assertThat(service.currentSummary(ContentLanguage.EN)).isEmpty();
    }

    @Test
    void summaryUsesRequestedLanguageOnlyAndSortsChildren() {
        var service = new PublicProfileSummaryQueryService(new FakeProfileRepository(profile(ContentLanguage.EN)));

        var summary = service.currentSummary(ContentLanguage.EN).orElseThrow();

        assertThat(summary.shortBio()).isEqualTo("Short EN");
        assertThat(summary.externalLinks()).extracting(link -> link.sortOrder()).containsExactly(1, 2);
        assertThat(summary.technicalFocusAreas()).extracting(area -> area.sortOrder()).containsExactly(1, 2);
        assertThat(summary.educationSummaries()).extracting(education -> education.sortOrder()).containsExactly(1, 2);
        assertThat(summary.currentFocusItems()).extracting(item -> item.sortOrder()).containsExactly(1, 2);
    }

    private static PersonalProfile profile(ContentLanguage language) {
        return PersonalProfile.create(
                ProfileId.newId(),
                DisplayName.of("Enes"),
                true,
                List.of(new ProfileLocalization(
                        ProfileLocalizationId.newId(),
                        language,
                        ShortBio.of("Short " + language.name()),
                        LongBio.of("Long " + language.name()),
                        LocationText.of("Istanbul"),
                        List.of(
                                new TechnicalFocusArea(TechnicalFocusAreaId.newId(), FocusAreaName.of("Second"), null, SortOrder.of(2)),
                                new TechnicalFocusArea(TechnicalFocusAreaId.newId(), FocusAreaName.of("First"), null, SortOrder.of(1))),
                        List.of(
                                new EducationSummary(EducationSummaryId.newId(), InstitutionName.of("Second"), ProgramName.of("Program"), null, SortOrder.of(2)),
                                new EducationSummary(EducationSummaryId.newId(), InstitutionName.of("First"), ProgramName.of("Program"), null, SortOrder.of(1))),
                        List.of(
                                new CurrentFocusItem(CurrentFocusItemId.newId(), FocusItemText.of("Second"), SortOrder.of(2)),
                                new CurrentFocusItem(CurrentFocusItemId.newId(), FocusItemText.of("First"), SortOrder.of(1))))),
                List.of(
                        new ExternalProfileLink(ExternalProfileLinkId.newId(), LinkLabel.of("Second"), ExternalUrl.of("https://example.test/2"), SortOrder.of(2)),
                        new ExternalProfileLink(ExternalProfileLinkId.newId(), LinkLabel.of("First"), ExternalUrl.of("https://example.test/1"), SortOrder.of(1))),
                Instant.parse("2026-06-16T10:00:00Z"));
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
}
