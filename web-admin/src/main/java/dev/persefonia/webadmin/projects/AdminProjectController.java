package dev.persefonia.webadmin.projects;

import dev.persefonia.profileportfolio.application.exception.ProjectApplicationException;
import dev.persefonia.profileportfolio.application.exception.ProjectCommandRejectedException;
import dev.persefonia.profileportfolio.application.exception.ProjectNotFoundException;
import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.query.AdminProjectEditView;
import dev.persefonia.profileportfolio.application.query.AdminProjectFormOptions;
import dev.persefonia.profileportfolio.application.service.ProjectAdminQueryService;
import dev.persefonia.profileportfolio.application.service.ProjectCommandGateway;
import dev.persefonia.webadmin.settings.PortfolioAdminActorResolver;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public final class AdminProjectController {
    private static final String NOT_FOUND = "Project was not found.";

    private final ProjectAdminQueryService queries;
    private final ProjectCommandGateway commands;
    private final PortfolioAdminActorResolver actors;
    private final AdminProjectPageChromeFactory chrome;
    private final AdminProjectFormMapper mapper = new AdminProjectFormMapper();
    private final AdminProjectFormValidator validator = new AdminProjectFormValidator();

    public AdminProjectController(
            ProjectAdminQueryService queries,
            ProjectCommandGateway commands,
            PortfolioAdminActorResolver actors,
            AdminProjectPageChromeFactory chrome) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
    }

    @GetMapping("/admin/projects")
    public String list(
            Authentication authentication,
            CsrfToken csrfToken,
            @RequestParam(name = "created", required = false) String created,
            @RequestParam(name = "saved", required = false) String saved,
            Model model) {
        model.addAttribute("page", new AdminProjectListPage(
                chrome.create(authentication, csrfToken),
                queries.list(),
                successMessage(created, saved)));
        return "admin/projects/list";
    }

    @GetMapping("/admin/projects/new")
    public String newForm(Authentication authentication, CsrfToken csrfToken, Model model) {
        AdminProjectFormOptions options = options();
        model.addAttribute("page", createFormPage(
                chrome.create(authentication, csrfToken),
                mapper.newForm(options.defaultLanguage()),
                options,
                List.of(),
                List.of()));
        return "admin/projects/new";
    }

    @PostMapping("/admin/projects")
    public String create(
            Authentication authentication,
            CsrfToken csrfToken,
            @ModelAttribute AdminProjectForm form,
            Model model) {
        AdminProjectFormOptions options = options();
        List<AdminProjectFieldError> errors = validator.validate(form, options.defaultLanguage());
        if (!errors.isEmpty()) {
            model.addAttribute("page", createFormPage(
                    chrome.create(authentication, csrfToken), form, options, errors, List.of()));
            return "admin/projects/new";
        }
        try {
            var result = commands.create(mapper.toCreateCommand(actors.resolve(authentication), form, Instant.now()));
            return "redirect:/admin/projects/" + result.projectId() + "/edit?created";
        } catch (ProjectCommandRejectedException exception) {
            model.addAttribute("page", createFormPage(
                    chrome.create(authentication, csrfToken), form, options, commandError(exception), List.of()));
        } catch (IllegalArgumentException | ProjectApplicationException exception) {
            model.addAttribute("page", createFormPage(
                    chrome.create(authentication, csrfToken), form, options, List.of(), List.of("Project could not be created.")));
        }
        return "admin/projects/new";
    }

    @GetMapping("/admin/projects/{projectId}/edit")
    public String edit(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("projectId") String projectId,
            @RequestParam(name = "created", required = false) String created,
            @RequestParam(name = "saved", required = false) String saved,
            Model model) {
        AdminProjectEditView project = project(projectId);
        model.addAttribute("page", editFormPage(
                chrome.create(authentication, csrfToken),
                mapper.toForm(project),
                project,
                options(),
                List.of(),
                List.of(),
                editSuccessMessage(created, saved)));
        return "admin/projects/edit";
    }

    @PostMapping("/admin/projects/{projectId}")
    public String update(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("projectId") String projectId,
            @ModelAttribute AdminProjectForm form,
            Model model) {
        UUID id = parse(projectId);
        AdminProjectFormOptions options = options();
        AdminProjectEditView project = project(projectId);
        List<AdminProjectFieldError> errors = validator.validate(form, options.defaultLanguage());
        if (!errors.isEmpty()) {
            model.addAttribute("page", editFormPage(
                    chrome.create(authentication, csrfToken), form, project, options, errors, List.of(), null));
            return "admin/projects/edit";
        }
        try {
            commands.update(mapper.toUpdateCommand(actors.resolve(authentication), id, form, Instant.now()));
            return "redirect:/admin/projects/" + projectId + "/edit?saved";
        } catch (ProjectNotFoundException exception) {
            throw notFound();
        } catch (ProjectCommandRejectedException exception) {
            model.addAttribute("page", editFormPage(
                    chrome.create(authentication, csrfToken), form, project, options, commandError(exception), List.of(), null));
        } catch (IllegalArgumentException | ProjectApplicationException exception) {
            model.addAttribute("page", editFormPage(
                    chrome.create(authentication, csrfToken), form, project, options, List.of(), List.of("Project could not be saved."), null));
        }
        return "admin/projects/edit";
    }

    private AdminProjectEditView project(String projectId) {
        try {
            return queries.edit(parse(projectId));
        } catch (ProjectNotFoundException exception) {
            throw notFound();
        }
    }

    private AdminProjectFormOptions options() {
        try {
            return queries.formOptions();
        } catch (SitePresentationSettingsNotInitializedException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Site settings are not initialized.");
        }
    }

    private static AdminProjectFormPage createFormPage(
            AdminProjectPageChrome chrome,
            AdminProjectForm form,
            AdminProjectFormOptions options,
            List<AdminProjectFieldError> fieldErrors,
            List<String> globalErrors) {
        return new AdminProjectFormPage(
                chrome,
                form,
                "Create project",
                "/admin/projects",
                options.defaultLanguage(),
                options.assignableTags(),
                List.of(),
                fieldErrors,
                globalErrors,
                null);
    }

    private static AdminProjectFormPage editFormPage(
            AdminProjectPageChrome chrome,
            AdminProjectForm form,
            AdminProjectEditView project,
            AdminProjectFormOptions options,
            List<AdminProjectFieldError> fieldErrors,
            List<String> globalErrors,
            String successMessage) {
        return new AdminProjectFormPage(
                chrome,
                form,
                "Edit project",
                "/admin/projects/" + project.id(),
                project.defaultLanguage(),
                options.assignableTags(),
                project.assignedTags(),
                fieldErrors,
                globalErrors,
                successMessage);
    }

    private static List<AdminProjectFieldError> commandError(ProjectCommandRejectedException exception) {
        return switch (exception.reason()) {
            case DUPLICATE_SLUG -> List.of(new AdminProjectFieldError("slug", "This slug is already in use."));
            case MISSING_TAG -> List.of(new AdminProjectFieldError("tagIds", "One or more selected tags no longer exist."));
            case ARCHIVED_TAG -> List.of(new AdminProjectFieldError("tagIds", "Archived tags cannot be newly assigned."));
        };
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    private static String successMessage(String created, String saved) {
        if (created != null) return "Project created.";
        return saved != null ? "Project saved." : null;
    }

    private static String editSuccessMessage(String created, String saved) {
        if (created != null) return "Project created.";
        return saved != null ? "Project saved." : null;
    }
}
