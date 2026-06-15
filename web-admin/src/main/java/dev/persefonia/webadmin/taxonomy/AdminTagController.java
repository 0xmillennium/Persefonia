package dev.persefonia.webadmin.taxonomy;

import dev.persefonia.taxonomy.application.command.ArchiveTagCommand;
import dev.persefonia.taxonomy.application.command.CreateTagCommand;
import dev.persefonia.taxonomy.application.command.UpdateTagCommand;
import dev.persefonia.taxonomy.application.exception.TagCommandRejectedException;
import dev.persefonia.taxonomy.application.exception.TagNotFoundException;
import dev.persefonia.taxonomy.application.query.TagEditView;
import dev.persefonia.taxonomy.application.service.TagAdminQueryService;
import dev.persefonia.taxonomy.application.service.TagCommandGateway;
import dev.persefonia.taxonomy.domain.model.TagId;
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
public final class AdminTagController {
    private static final String NOT_FOUND = "Tag was not found.";

    private final TagCommandGateway commands;
    private final TagAdminQueryService queries;
    private final TaxonomyAdminActorResolver actors;
    private final AdminTagPageChromeFactory chrome;

    public AdminTagController(
            TagCommandGateway commands,
            TagAdminQueryService queries,
            TaxonomyAdminActorResolver actors,
            AdminTagPageChromeFactory chrome) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
    }

    @GetMapping("/admin/tags")
    public String list(
            Authentication authentication,
            CsrfToken csrfToken,
            @RequestParam(name = "created", required = false) String created,
            @RequestParam(name = "saved", required = false) String saved,
            @RequestParam(name = "archived", required = false) String archived,
            Model model) {
        model.addAttribute("page", new AdminTagListPage(
                chrome.create(authentication, csrfToken),
                queries.list(actors.resolve(authentication)),
                successMessage(created, saved, archived)));
        return "admin/tags/list";
    }

    @GetMapping("/admin/tags/new")
    public String newForm(Authentication authentication, CsrfToken csrfToken, Model model) {
        model.addAttribute("page", formPage(chrome.create(authentication, csrfToken), new AdminTagForm(), null, List.of(), List.of()));
        return "admin/tags/new";
    }

    @PostMapping("/admin/tags")
    public String create(
            Authentication authentication,
            CsrfToken csrfToken,
            @ModelAttribute AdminTagForm form,
            Model model) {
        var pageChrome = chrome.create(authentication, csrfToken);
        try {
            var result = commands.create(new CreateTagCommand(
                    actors.resolve(authentication), form.getName(), form.getSlug(), form.getDescription(), Instant.now()));
            return "redirect:/admin/tags/" + result.tagId().value() + "/edit?created";
        } catch (TagCommandRejectedException exception) {
            model.addAttribute("page", formPage(pageChrome, form, null, duplicateError(exception), List.of()));
        } catch (IllegalArgumentException exception) {
            model.addAttribute("page", formPage(pageChrome, form, null, validationError(form), List.of()));
        }
        return "admin/tags/new";
    }

    @GetMapping("/admin/tags/{tagId}/edit")
    public String edit(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("tagId") String tagId,
            Model model) {
        TagEditView tag = tag(authentication, tagId);
        model.addAttribute("page", formPage(chrome.create(authentication, csrfToken), form(tag), tag, List.of(), List.of()));
        return "admin/tags/edit";
    }

    @PostMapping("/admin/tags/{tagId}")
    public String update(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("tagId") String tagId,
            @ModelAttribute AdminTagForm form,
            Model model) {
        TagId id = parse(tagId);
        try {
            commands.update(new UpdateTagCommand(
                    actors.resolve(authentication), id, form.getName(), form.getSlug(), form.getDescription(), Instant.now()));
            return "redirect:/admin/tags/" + tagId + "/edit?saved";
        } catch (TagNotFoundException exception) {
            throw notFound();
        } catch (TagCommandRejectedException exception) {
            TagEditView tag = tag(authentication, tagId);
            model.addAttribute("page", formPage(
                    chrome.create(authentication, csrfToken), form, tag, duplicateError(exception), List.of()));
        } catch (IllegalArgumentException exception) {
            TagEditView tag = tag(authentication, tagId);
            model.addAttribute("page", formPage(
                    chrome.create(authentication, csrfToken), form, tag, validationError(form), List.of()));
        } catch (IllegalStateException exception) {
            TagEditView tag = tag(authentication, tagId);
            model.addAttribute("page", formPage(
                    chrome.create(authentication, csrfToken), form, tag, List.of(), List.of("The tag could not be updated.")));
        }
        return "admin/tags/edit";
    }

    @PostMapping("/admin/tags/{tagId}/archive")
    public String archive(Authentication authentication, @PathVariable("tagId") String tagId) {
        try {
            commands.archive(new ArchiveTagCommand(actors.resolve(authentication), parse(tagId), Instant.now()));
            return "redirect:/admin/tags?archived";
        } catch (TagNotFoundException exception) {
            throw notFound();
        }
    }

    private TagEditView tag(Authentication authentication, String tagId) {
        try {
            return queries.edit(actors.resolve(authentication), parse(tagId));
        } catch (TagNotFoundException exception) {
            throw notFound();
        }
    }

    private static AdminTagFormPage formPage(
            AdminTagPageChrome chrome,
            AdminTagForm form,
            TagEditView tag,
            List<AdminTagFieldError> fieldErrors,
            List<String> globalErrors) {
        boolean create = tag == null;
        return new AdminTagFormPage(
                chrome,
                form,
                create ? "Create tag" : "Edit tag",
                create ? "/admin/tags" : "/admin/tags/" + tag.tagId().value(),
                create ? null : tag.status().name(),
                !create && tag.status().name().equals("ACTIVE") ? "/admin/tags/" + tag.tagId().value() + "/archive" : null,
                fieldErrors,
                globalErrors);
    }

    private static AdminTagForm form(TagEditView tag) {
        AdminTagForm form = new AdminTagForm();
        form.setName(tag.name());
        form.setSlug(tag.slug());
        form.setDescription(tag.description());
        return form;
    }

    private static List<AdminTagFieldError> duplicateError(TagCommandRejectedException exception) {
        return switch (exception.reason()) {
            case DUPLICATE_SLUG -> List.of(new AdminTagFieldError("slug", "This slug is already in use."));
            case DUPLICATE_NORMALIZED_NAME -> List.of(new AdminTagFieldError("name", "This tag name is already in use."));
        };
    }

    private static List<AdminTagFieldError> validationError(AdminTagForm form) {
        if (form.getName().isBlank()) {
            return List.of(new AdminTagFieldError("name", "This field is required."));
        }
        return List.of(new AdminTagFieldError("slug", "Provide a valid name, slug, and description."));
    }

    private static TagId parse(String value) {
        try {
            return TagId.from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    private static String successMessage(String created, String saved, String archived) {
        if (created != null) return "Tag created.";
        if (saved != null) return "Tag saved.";
        return archived != null ? "Tag archived." : null;
    }
}
