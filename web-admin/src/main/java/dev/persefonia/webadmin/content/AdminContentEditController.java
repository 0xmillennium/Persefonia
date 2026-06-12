package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.exception.ContentApplicationException;
import dev.persefonia.contentpublishing.application.exception.ContentNotFoundException;
import dev.persefonia.contentpublishing.application.service.ContentAdminQueryService;
import dev.persefonia.contentpublishing.application.service.ContentCommandGateway;
import dev.persefonia.contentpublishing.domain.content.ContentId;
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
public final class AdminContentEditController {
    private static final String NOT_FOUND = "Content was not found.";
    private static final String UPDATE_FAILED = "The content could not be updated.";

    private final ContentCommandGateway commands;
    private final ContentAdminQueryService queries;
    private final ContentAdminActorResolver actors;
    private final AdminContentPageChromeFactory chrome;
    private final AdminContentFormValidator validator;
    private final AdminContentFormMapper mapper;
    private final AdminContentViewModelFactory views;

    public AdminContentEditController(
            ContentCommandGateway commands,
            ContentAdminQueryService queries,
            ContentAdminActorResolver actors,
            AdminContentPageChromeFactory chrome,
            AdminContentFormValidator validator,
            AdminContentFormMapper mapper,
            AdminContentViewModelFactory views) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.views = Objects.requireNonNull(views, "views");
    }

    @GetMapping("/admin/content/{contentId}/edit")
    public String edit(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("contentId") String contentId,
            @RequestParam(name = "created", required = false) String created,
            @RequestParam(name = "saved", required = false) String saved,
            @RequestParam(name = "published", required = false) String published,
            @RequestParam(name = "unpublished", required = false) String unpublished,
            @RequestParam(name = "publishFailed", required = false) String publishFailed,
            @RequestParam(name = "unpublishFailed", required = false) String unpublishFailed,
            @RequestParam(name = "archiveFailed", required = false) String archiveFailed,
            Model model) {
        var pageChrome = chrome.create(authentication, csrfToken);
        ContentId id = parseContentId(contentId);
        try {
            var result = queries.getContentForAdmin(actors.resolve(authentication), id);
            String success = successMessage(created, saved, published, unpublished);
            var page = views.edit(pageChrome, result, mapper.from(result), success);
            var errors = lifecycleErrors(publishFailed, unpublishFailed, archiveFailed);
            model.addAttribute("page", errors.isEmpty() ? page : views.withErrors(page, List.of(), errors));
        } catch (ContentNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return "admin/content/form";
    }

    @PostMapping("/admin/content/{contentId}")
    public String update(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("contentId") String contentId,
            @ModelAttribute AdminContentForm form,
            Model model) {
        var page = views.editSubmission(chrome.create(authentication, csrfToken), contentId, form);
        ContentId id = parseContentId(contentId);
        var errors = validator.validate(form, false);
        if (!errors.isEmpty()) {
            model.addAttribute("page", views.withErrors(page, errors, List.of()));
            return "admin/content/form";
        }
        try {
            commands.updateDraft(mapper.toUpdate(
                    actors.resolve(authentication), id, form, Instant.now()));
            return "redirect:/admin/content/" + contentId + "/edit?saved";
        } catch (ContentNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        } catch (ContentApplicationException | IllegalArgumentException | IllegalStateException exception) {
            model.addAttribute("page", views.withErrors(page, List.of(), List.of(UPDATE_FAILED)));
        }
        return "admin/content/form";
    }

    private static ContentId parseContentId(String value) {
        try {
            return ContentId.from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
    }

    private static String successMessage(String created, String saved, String published, String unpublished) {
        if (created != null) {
            return "Draft created.";
        }
        if (saved != null) {
            return "Changes saved.";
        }
        if (published != null) {
            return "Content published.";
        }
        return unpublished != null ? "Content unpublished." : null;
    }

    private static List<String> lifecycleErrors(String publishFailed, String unpublishFailed, String archiveFailed) {
        if (publishFailed != null) {
            return List.of("The content could not be published.");
        }
        if (unpublishFailed != null) {
            return List.of("The content could not be unpublished.");
        }
        if (archiveFailed != null) {
            return List.of("The content could not be archived.");
        }
        return List.of();
    }
}
