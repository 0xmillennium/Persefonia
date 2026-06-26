package dev.persefonia.webadmin.projects;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import dev.persefonia.profileportfolio.application.command.CreateProjectCommand;
import dev.persefonia.profileportfolio.application.command.ProjectCaseStudySectionInput;
import dev.persefonia.profileportfolio.application.command.ProjectLinkInput;
import dev.persefonia.profileportfolio.application.command.ProjectLocalizationInput;
import dev.persefonia.profileportfolio.application.command.ProjectTechnologyInput;
import dev.persefonia.profileportfolio.application.command.UpdateProjectCommand;
import dev.persefonia.profileportfolio.application.query.AdminProjectCaseStudySectionView;
import dev.persefonia.profileportfolio.application.query.AdminProjectEditView;
import dev.persefonia.profileportfolio.application.query.AdminProjectLocalizationView;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class AdminProjectFormMapper {
    AdminProjectForm newForm(String defaultLanguage) {
        AdminProjectForm form = new AdminProjectForm();
        form.setStatus("EXPERIMENT");
        if ("EN".equals(defaultLanguage)) {
            form.setEnEnabled(true);
        } else {
            form.setTrEnabled(true);
        }
        return form;
    }

    AdminProjectForm toForm(AdminProjectEditView view) {
        AdminProjectForm form = new AdminProjectForm();
        form.setStatus(view.status());
        form.setVisibility(view.visibility());
        form.setFeatured(view.featured());
        form.setSortOrder(view.sortOrder() == null ? "" : view.sortOrder().toString());
        view.localizations().forEach(localization -> apply(form, localization));
        form.setTechnologies(lines(view.technologies().stream()
                .map(technology -> technology.name() + " | " + technology.category())
                .toList()));
        form.setLinks(lines(view.links().stream()
                .map(link -> link.label() + " | " + link.url() + " | " + link.linkType())
                .toList()));
        form.setTagIds(view.assignedTags().stream().map(tag -> tag.id().toString()).toList());
        return form;
    }

    CreateProjectCommand toCreateCommand(PortfolioCommandActor actor, AdminProjectForm form, Instant now) {
        return new CreateProjectCommand(
                actor,
                form.getStatus(),
                form.getVisibility(),
                form.isFeatured(),
                sortOrder(form),
                tagIds(form),
                localizations(form),
                technologies(form.getTechnologies()),
                links(form.getLinks()),
                now);
    }

    UpdateProjectCommand toUpdateCommand(
            PortfolioCommandActor actor,
            UUID projectId,
            AdminProjectForm form,
            Instant now) {
        return new UpdateProjectCommand(
                actor,
                projectId,
                form.getStatus(),
                form.getVisibility(),
                form.isFeatured(),
                sortOrder(form),
                tagIds(form),
                localizations(form),
                technologies(form.getTechnologies()),
                links(form.getLinks()),
                now);
    }

    private static void apply(AdminProjectForm form, AdminProjectLocalizationView localization) {
        if ("TR".equals(localization.language())) {
            form.setTrEnabled(true);
            form.setTrSlug(localization.slug());
            form.setTrTitle(localization.title());
            form.setTrSummary(localization.summary());
            applyTrSections(form, localization.sections());
        } else if ("EN".equals(localization.language())) {
            form.setEnEnabled(true);
            form.setEnSlug(localization.slug());
            form.setEnTitle(localization.title());
            form.setEnSummary(localization.summary());
            applyEnSections(form, localization.sections());
        }
    }

    private static List<ProjectLocalizationInput> localizations(AdminProjectForm form) {
        java.util.ArrayList<ProjectLocalizationInput> values = new java.util.ArrayList<>();
        if (form.isTrEnabled()) {
            values.add(new ProjectLocalizationInput(
                    "TR",
                    form.getTrSlug().trim(),
                    form.getTrTitle().trim(),
                    form.getTrSummary().trim(),
                    trCaseStudySections(form)));
        }
        if (form.isEnEnabled()) {
            values.add(new ProjectLocalizationInput(
                    "EN",
                    form.getEnSlug().trim(),
                    form.getEnTitle().trim(),
                    form.getEnSummary().trim(),
                    enCaseStudySections(form)));
        }
        return values;
    }

    private static List<ProjectCaseStudySectionInput> trCaseStudySections(AdminProjectForm form) {
        java.util.ArrayList<ProjectCaseStudySectionInput> sections = new java.util.ArrayList<>();
        addSection(sections, "PROBLEM", form.getTrProblem(), 1);
        addSection(sections, "CONTEXT", form.getTrContext(), 2);
        addSection(sections, "ROLE", form.getTrRole(), 3);
        addSection(sections, "APPROACH", form.getTrApproach(), 4);
        addSection(sections, "ARCHITECTURE", form.getTrArchitecture(), 5);
        addSection(sections, "DECISIONS", form.getTrDecisions(), 6);
        addSection(sections, "TRADEOFFS", form.getTrTradeoffs(), 7);
        addSection(sections, "RESULT", form.getTrResult(), 8);
        addSection(sections, "LESSONS", form.getTrLessons(), 9);
        addSection(sections, "FUTURE", form.getTrFuture(), 10);
        return sections;
    }

    private static List<ProjectCaseStudySectionInput> enCaseStudySections(AdminProjectForm form) {
        java.util.ArrayList<ProjectCaseStudySectionInput> sections = new java.util.ArrayList<>();
        addSection(sections, "PROBLEM", form.getEnProblem(), 1);
        addSection(sections, "CONTEXT", form.getEnContext(), 2);
        addSection(sections, "ROLE", form.getEnRole(), 3);
        addSection(sections, "APPROACH", form.getEnApproach(), 4);
        addSection(sections, "ARCHITECTURE", form.getEnArchitecture(), 5);
        addSection(sections, "DECISIONS", form.getEnDecisions(), 6);
        addSection(sections, "TRADEOFFS", form.getEnTradeoffs(), 7);
        addSection(sections, "RESULT", form.getEnResult(), 8);
        addSection(sections, "LESSONS", form.getEnLessons(), 9);
        addSection(sections, "FUTURE", form.getEnFuture(), 10);
        return sections;
    }

    private static List<ProjectTechnologyInput> technologies(String value) {
        List<String> lines = nonBlankLines(value);
        java.util.ArrayList<ProjectTechnologyInput> technologies = new java.util.ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String[] parts = split(lines.get(index), 2);
            technologies.add(new ProjectTechnologyInput(parts[0].trim(), parts[1].trim(), index + 1));
        }
        return technologies;
    }

    private static List<ProjectLinkInput> links(String value) {
        List<String> lines = nonBlankLines(value);
        java.util.ArrayList<ProjectLinkInput> links = new java.util.ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String[] parts = split(lines.get(index), 3);
            links.add(new ProjectLinkInput(parts[0].trim(), parts[1].trim(), parts[2].trim(), index + 1));
        }
        return links;
    }

    private static Set<UUID> tagIds(AdminProjectForm form) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (String value : form.getTagIds()) {
            if (value != null && !value.isBlank()) {
                ids.add(UUID.fromString(value));
            }
        }
        return ids;
    }

    private static Integer sortOrder(AdminProjectForm form) {
        return form.getSortOrder().isBlank() ? null : Integer.valueOf(form.getSortOrder().trim());
    }

    private static void addSection(
            java.util.ArrayList<ProjectCaseStudySectionInput> sections,
            String type,
            String body,
            int sortOrder) {
        if (body != null && !body.isBlank()) {
            sections.add(new ProjectCaseStudySectionInput(type, body.trim(), sortOrder));
        }
    }

    private static void applyTrSections(AdminProjectForm form, List<AdminProjectCaseStudySectionView> sections) {
        for (AdminProjectCaseStudySectionView section : sections) {
            switch (section.type()) {
                case "PROBLEM" -> form.setTrProblem(section.body());
                case "CONTEXT" -> form.setTrContext(section.body());
                case "ROLE" -> form.setTrRole(section.body());
                case "APPROACH" -> form.setTrApproach(section.body());
                case "ARCHITECTURE" -> form.setTrArchitecture(section.body());
                case "DECISIONS" -> form.setTrDecisions(section.body());
                case "TRADEOFFS" -> form.setTrTradeoffs(section.body());
                case "RESULT" -> form.setTrResult(section.body());
                case "LESSONS" -> form.setTrLessons(section.body());
                case "FUTURE" -> form.setTrFuture(section.body());
                default -> { }
            }
        }
    }

    private static void applyEnSections(AdminProjectForm form, List<AdminProjectCaseStudySectionView> sections) {
        for (AdminProjectCaseStudySectionView section : sections) {
            switch (section.type()) {
                case "PROBLEM" -> form.setEnProblem(section.body());
                case "CONTEXT" -> form.setEnContext(section.body());
                case "ROLE" -> form.setEnRole(section.body());
                case "APPROACH" -> form.setEnApproach(section.body());
                case "ARCHITECTURE" -> form.setEnArchitecture(section.body());
                case "DECISIONS" -> form.setEnDecisions(section.body());
                case "TRADEOFFS" -> form.setEnTradeoffs(section.body());
                case "RESULT" -> form.setEnResult(section.body());
                case "LESSONS" -> form.setEnLessons(section.body());
                case "FUTURE" -> form.setEnFuture(section.body());
                default -> { }
            }
        }
    }

    private static String lines(List<String> lines) {
        return String.join("\n", lines);
    }

    private static List<String> nonBlankLines(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : value.lines().map(line -> line.trim()).filter(line -> !line.isBlank()).toList();
    }

    private static String[] split(String line, int expectedParts) {
        String[] parts = line.split("\\|", expectedParts);
        if (parts.length != expectedParts) {
            throw new IllegalArgumentException("Invalid project form line.");
        }
        return parts;
    }
}
