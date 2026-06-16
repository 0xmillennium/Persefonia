package dev.persefonia.profileportfolio.domain.profile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalProfileTest {
    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");

    @Test
    void rejectsBlankDisplayName() {
        assertThatThrownBy(() -> DisplayName.of(" "))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsDuplicateLocalizationLanguage() {
        assertThatThrownBy(() -> profile(List.of(localization(ContentLanguage.TR), localization(ContentLanguage.TR)), List.of()))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsDuplicateExternalLinkSortOrder() {
        assertThatThrownBy(() -> profile(List.of(localization(ContentLanguage.TR)), List.of(link(1), link(1))))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsDuplicateTechnicalFocusAreaSortOrder() {
        assertThatThrownBy(() -> new ProfileLocalization(
                ProfileLocalizationId.newId(),
                ContentLanguage.TR,
                ShortBio.of("Short"),
                LongBio.of("Long"),
                null,
                List.of(focusArea(1), focusArea(1)),
                List.of(),
                List.of()))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsDuplicateEducationSummarySortOrder() {
        assertThatThrownBy(() -> new ProfileLocalization(
                ProfileLocalizationId.newId(),
                ContentLanguage.TR,
                ShortBio.of("Short"),
                LongBio.of("Long"),
                null,
                List.of(),
                List.of(education(1), education(1)),
                List.of()))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsDuplicateCurrentFocusItemSortOrder() {
        assertThatThrownBy(() -> new ProfileLocalization(
                ProfileLocalizationId.newId(),
                ContentLanguage.TR,
                ShortBio.of("Short"),
                LongBio.of("Long"),
                null,
                List.of(),
                List.of(),
                List.of(focusItem(1), focusItem(1))))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsInvalidExternalUrl() {
        assertThatThrownBy(() -> ExternalUrl.of("not a url"))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void updateActiveProfileUpdatesProfileAtomically() {
        PersonalProfile profile = PersonalProfile.create(
                ProfileId.newId(),
                DisplayName.of("Old"),
                false,
                List.of(localization(ContentLanguage.TR)),
                List.of(link(1)),
                NOW);

        Instant updatedAt = NOW.plusSeconds(60);
        profile.updateActiveProfile(
                DisplayName.of("New"),
                List.of(localization(ContentLanguage.EN)),
                List.of(link(2)),
                updatedAt);

        assertThat(profile.displayName().value()).isEqualTo("New");
        assertThat(profile.active()).isTrue();
        assertThat(profile.localizations()).extracting(ProfileLocalization::language).containsExactly(ContentLanguage.EN);
        assertThat(profile.externalLinks()).extracting(link -> link.sortOrder().value()).containsExactly(2);
        assertThat(profile.updatedAt()).isEqualTo(updatedAt);
        assertThat(profile.version().value()).isEqualTo(1);
    }

    @Test
    void updateActiveProfileRejectsDuplicateLocalizationLanguage() {
        PersonalProfile profile = profile(List.of(localization(ContentLanguage.TR)), List.of());

        assertThatThrownBy(() -> profile.updateActiveProfile(
                DisplayName.of("Enes"),
                List.of(localization(ContentLanguage.EN), localization(ContentLanguage.EN)),
                List.of(),
                NOW.plusSeconds(1)))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void updateActiveProfileRejectsDuplicateExternalLinkSortOrder() {
        PersonalProfile profile = profile(List.of(localization(ContentLanguage.TR)), List.of());

        assertThatThrownBy(() -> profile.updateActiveProfile(
                DisplayName.of("Enes"),
                List.of(localization(ContentLanguage.TR)),
                List.of(link(1), link(1)),
                NOW.plusSeconds(1)))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void updateActiveProfileRejectsNullNow() {
        PersonalProfile profile = profile(List.of(localization(ContentLanguage.TR)), List.of());

        assertThatThrownBy(() -> profile.updateActiveProfile(
                DisplayName.of("Enes"),
                List.of(localization(ContentLanguage.TR)),
                List.of(),
                null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void updateActiveProfileRejectsUpdatedAtBeforeCreatedAt() {
        PersonalProfile profile = profile(List.of(localization(ContentLanguage.TR)), List.of());

        assertThatThrownBy(() -> profile.updateActiveProfile(
                DisplayName.of("Enes"),
                List.of(localization(ContentLanguage.TR)),
                List.of(),
                NOW.minusSeconds(1)))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void hasLocalizationChecksLanguageWithoutFallback() {
        PersonalProfile profile = profile(List.of(localization(ContentLanguage.TR)), List.of());

        assertThat(profile.hasLocalization(ContentLanguage.TR)).isTrue();
        assertThat(profile.hasLocalization(ContentLanguage.EN)).isFalse();
    }

    private static PersonalProfile profile(List<ProfileLocalization> localizations, List<ExternalProfileLink> links) {
        return PersonalProfile.create(
                ProfileId.newId(),
                DisplayName.of("Enes"),
                true,
                localizations,
                links,
                NOW);
    }

    private static ProfileLocalization localization(ContentLanguage language) {
        return new ProfileLocalization(
                ProfileLocalizationId.newId(),
                language,
                ShortBio.of("Short"),
                LongBio.of("Long"),
                LocationText.of("Istanbul"),
                List.of(),
                List.of(),
                List.of());
    }

    private static ExternalProfileLink link(int sortOrder) {
        return new ExternalProfileLink(
                ExternalProfileLinkId.newId(),
                LinkLabel.of("GitHub"),
                ExternalUrl.of("https://example.test"),
                SortOrder.of(sortOrder));
    }

    private static TechnicalFocusArea focusArea(int sortOrder) {
        return new TechnicalFocusArea(
                TechnicalFocusAreaId.newId(),
                FocusAreaName.of("Architecture"),
                null,
                SortOrder.of(sortOrder));
    }

    private static EducationSummary education(int sortOrder) {
        return new EducationSummary(
                EducationSummaryId.newId(),
                InstitutionName.of("University"),
                ProgramName.of("Computer Science"),
                null,
                SortOrder.of(sortOrder));
    }

    private static CurrentFocusItem focusItem(int sortOrder) {
        return new CurrentFocusItem(
                CurrentFocusItemId.newId(),
                FocusItemText.of("Build systems"),
                SortOrder.of(sortOrder));
    }
}
