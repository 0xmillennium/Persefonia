package dev.persefonia.webadmin.profile;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import dev.persefonia.profileportfolio.application.command.CurrentFocusItemInput;
import dev.persefonia.profileportfolio.application.command.EducationSummaryInput;
import dev.persefonia.profileportfolio.application.command.ExternalProfileLinkInput;
import dev.persefonia.profileportfolio.application.command.ProfileLocalizationInput;
import dev.persefonia.profileportfolio.application.command.TechnicalFocusAreaInput;
import dev.persefonia.profileportfolio.application.command.UpsertActivePersonalProfileCommand;
import dev.persefonia.profileportfolio.application.query.AdminCurrentFocusItemView;
import dev.persefonia.profileportfolio.application.query.AdminEducationSummaryView;
import dev.persefonia.profileportfolio.application.query.AdminExternalProfileLinkView;
import dev.persefonia.profileportfolio.application.query.AdminPersonalProfileView;
import dev.persefonia.profileportfolio.application.query.AdminProfileLocalizationView;
import dev.persefonia.profileportfolio.application.query.AdminTechnicalFocusAreaView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AdminPersonalProfileFormMapper {
    public AdminPersonalProfileForm toForm(AdminPersonalProfileView view) {
        AdminPersonalProfileForm form = new AdminPersonalProfileForm();
        form.setDisplayName(view.displayName());
        view.localization("TR").ifPresent(localization -> apply(form, localization));
        view.localization("EN").ifPresent(localization -> apply(form, localization));
        form.setExternalLinks(joinExternalLinks(view.externalLinks()));
        return form;
    }

    public UpsertActivePersonalProfileCommand toCommand(
            PortfolioCommandActor actor,
            AdminPersonalProfileForm form,
            Instant requestedAt) {
        List<ProfileLocalizationInput> localizations = new ArrayList<>();
        if (form.isTrEnabled()) {
            localizations.add(localization(
                    "TR",
                    form.getTrShortBio(),
                    form.getTrLongBio(),
                    form.getTrLocationText(),
                    form.getTrTechnicalFocusAreas(),
                    form.getTrEducationSummaries(),
                    form.getTrCurrentFocusItems()));
        }
        if (form.isEnEnabled()) {
            localizations.add(localization(
                    "EN",
                    form.getEnShortBio(),
                    form.getEnLongBio(),
                    form.getEnLocationText(),
                    form.getEnTechnicalFocusAreas(),
                    form.getEnEducationSummaries(),
                    form.getEnCurrentFocusItems()));
        }
        return new UpsertActivePersonalProfileCommand(
                actor,
                form.getDisplayName(),
                localizations,
                externalLinks(form.getExternalLinks()),
                requestedAt);
    }

    private static void apply(AdminPersonalProfileForm form, AdminProfileLocalizationView localization) {
        if ("TR".equals(localization.language())) {
            form.setTrEnabled(true);
            form.setTrShortBio(localization.shortBio());
            form.setTrLongBio(localization.longBio());
            form.setTrLocationText(localization.locationText());
            form.setTrTechnicalFocusAreas(joinFocusAreas(localization.technicalFocusAreas()));
            form.setTrEducationSummaries(joinEducationSummaries(localization.educationSummaries()));
            form.setTrCurrentFocusItems(joinFocusItems(localization.currentFocusItems()));
        } else if ("EN".equals(localization.language())) {
            form.setEnEnabled(true);
            form.setEnShortBio(localization.shortBio());
            form.setEnLongBio(localization.longBio());
            form.setEnLocationText(localization.locationText());
            form.setEnTechnicalFocusAreas(joinFocusAreas(localization.technicalFocusAreas()));
            form.setEnEducationSummaries(joinEducationSummaries(localization.educationSummaries()));
            form.setEnCurrentFocusItems(joinFocusItems(localization.currentFocusItems()));
        }
    }

    private static ProfileLocalizationInput localization(
            String language,
            String shortBio,
            String longBio,
            String locationText,
            String focusAreas,
            String educationSummaries,
            String focusItems) {
        return new ProfileLocalizationInput(
                language,
                shortBio.trim(),
                longBio.trim(),
                blankToNull(locationText),
                focusAreas(focusAreas),
                educationSummaries(educationSummaries),
                focusItems(focusItems));
    }

    private static List<TechnicalFocusAreaInput> focusAreas(String value) {
        List<String> lines = AdminPersonalProfileFormValidator.lines(value);
        List<TechnicalFocusAreaInput> inputs = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String[] parts = AdminPersonalProfileFormValidator.split(lines.get(index));
            inputs.add(new TechnicalFocusAreaInput(
                    parts[0],
                    parts.length == 2 ? blankToNull(parts[1]) : null,
                    index + 1));
        }
        return List.copyOf(inputs);
    }

    private static List<EducationSummaryInput> educationSummaries(String value) {
        List<String> lines = AdminPersonalProfileFormValidator.lines(value);
        List<EducationSummaryInput> inputs = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String[] parts = AdminPersonalProfileFormValidator.split(lines.get(index));
            inputs.add(new EducationSummaryInput(
                    parts[0],
                    parts[1],
                    parts.length == 3 ? blankToNull(parts[2]) : null,
                    index + 1));
        }
        return List.copyOf(inputs);
    }

    private static List<CurrentFocusItemInput> focusItems(String value) {
        List<String> lines = AdminPersonalProfileFormValidator.lines(value);
        List<CurrentFocusItemInput> inputs = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            inputs.add(new CurrentFocusItemInput(lines.get(index), index + 1));
        }
        return List.copyOf(inputs);
    }

    private static List<ExternalProfileLinkInput> externalLinks(String value) {
        List<String> lines = AdminPersonalProfileFormValidator.lines(value);
        List<ExternalProfileLinkInput> inputs = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String[] parts = AdminPersonalProfileFormValidator.split(lines.get(index));
            inputs.add(new ExternalProfileLinkInput(parts[0], parts[1], index + 1));
        }
        return List.copyOf(inputs);
    }

    private static String joinExternalLinks(List<AdminExternalProfileLinkView> links) {
        return links.stream()
                .sorted(Comparator.comparing(AdminExternalProfileLinkView::sortOrder))
                .map(link -> link.label() + " | " + link.url())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String joinFocusAreas(List<AdminTechnicalFocusAreaView> areas) {
        return areas.stream()
                .sorted(Comparator.comparing(AdminTechnicalFocusAreaView::sortOrder))
                .map(area -> area.description() == null || area.description().isBlank()
                        ? area.name()
                        : area.name() + " | " + area.description())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String joinEducationSummaries(List<AdminEducationSummaryView> summaries) {
        return summaries.stream()
                .sorted(Comparator.comparing(AdminEducationSummaryView::sortOrder))
                .map(summary -> summary.description() == null || summary.description().isBlank()
                        ? summary.institution() + " | " + summary.program()
                        : summary.institution() + " | " + summary.program() + " | " + summary.description())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String joinFocusItems(List<AdminCurrentFocusItemView> items) {
        return items.stream()
                .sorted(Comparator.comparing(AdminCurrentFocusItemView::sortOrder))
                .map(AdminCurrentFocusItemView::text)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
