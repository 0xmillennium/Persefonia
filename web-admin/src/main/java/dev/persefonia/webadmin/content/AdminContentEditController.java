package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.exception.ContentApplicationException;
import dev.persefonia.contentpublishing.application.exception.ContentCommandRejectedException;
import dev.persefonia.contentpublishing.application.exception.ContentNotFoundException;
import dev.persefonia.contentpublishing.application.exception.ContentTagAssignmentRejectedException;
import dev.persefonia.contentpublishing.application.command.AssignContentTagsCommand;
import dev.persefonia.contentpublishing.application.service.ContentAdminQueryService;
import dev.persefonia.contentpublishing.application.service.ContentCommandGateway;
import dev.persefonia.contentpublishing.application.service.ContentTagAssignmentGateway;
import dev.persefonia.contentpublishing.application.service.ContentTagAssignmentService;
import dev.persefonia.contentpublishing.application.service.TranslationGroupAdminQueryService;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.TagId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private final ContentTagAssignmentService tagAssignments;
    private final ContentTagAssignmentGateway tagAssignmentCommands;
    private final TranslationGroupAdminQueryService translations;

    public AdminContentEditController(
            ContentCommandGateway commands,
            ContentAdminQueryService queries,
            ContentAdminActorResolver actors,
            AdminContentPageChromeFactory chrome,
            AdminContentFormValidator validator,
            AdminContentFormMapper mapper,
            AdminContentViewModelFactory views,
            ContentTagAssignmentService tagAssignments,
            ContentTagAssignmentGateway tagAssignmentCommands,
            TranslationGroupAdminQueryService translations) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.views = Objects.requireNonNull(views, "views");
        this.tagAssignments = Objects.requireNonNull(tagAssignments, "tagAssignments");
        this.tagAssignmentCommands = Objects.requireNonNull(tagAssignmentCommands, "tagAssignmentCommands");
        this.translations = Objects.requireNonNull(translations, "translations");
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
            @RequestParam(name = "tagsSaved", required = false) String tagsSaved,
            @RequestParam(name = "translationGroupCreated", required = false) String translationGroupCreated,
            @RequestParam(name = "translationEntryAdded", required = false) String translationEntryAdded,
            @RequestParam(name = "translationEntryRemoved", required = false) String translationEntryRemoved,
            @RequestParam(name = "translationError", required = false) String translationError,
            Model model) {
        var pageChrome = chrome.create(authentication, csrfToken);
        ContentId id = parseContentId(contentId);
        try {
            var result = queries.getContentForAdmin(actors.resolve(authentication), id);
            String success = successMessage(created, saved, published, unpublished);
            var page = withTagAssignment(
                    views.edit(pageChrome, result, mapper.from(result), success),
                    authentication,
                    id,
                    Set.of(),
                    List.of(),
                    tagsSaved != null ? "Tag assignments saved." : null);
            var errors = lifecycleErrors(publishFailed, unpublishFailed, archiveFailed);
            model.addAttribute("page", errors.isEmpty() ? page : views.withErrors(page, List.of(), errors));
            model.addAttribute("translationSection", translations.loadSection(actors.resolve(authentication), id));
            model.addAttribute("translationMessage", translationMessage(
                    translationGroupCreated, translationEntryAdded, translationEntryRemoved));
            model.addAttribute("translationError", translationError(translationError));
        } catch (ContentNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return "admin/content/form";
    }

    private static String translationMessage(String created, String added, String removed) {
        if (created != null) {
            return "Translation group created.";
        }
        if (added != null) {
            return "Translation added to the group.";
        }
        return removed != null ? "Translation removed from the group." : null;
    }

    private static String translationError(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "ALREADY_IN_GROUP" -> "That content item already belongs to a translation group.";
            case "DUPLICATE_LANGUAGE" -> "The translation group already contains that language.";
            case "DIFFERENT_CONTENT_TYPE" -> "Translation entries must share the same content type.";
            case "LAST_ENTRY" -> "A translation group must keep at least one entry.";
            case "ENTRY_NOT_FOUND" -> "That translation entry no longer exists.";
            default -> "The translation group could not be updated.";
        };
    }

    @PostMapping("/admin/content/{contentId}/tags")
    public String assignTags(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("contentId") String contentId,
            @RequestParam(name = "tagId", required = false) List<String> tagIds,
            Model model) {
        ContentId id = parseContentId(contentId);
        List<String> submitted = tagIds == null ? List.of() : tagIds;
        Set<String> selected = Set.copyOf(submitted);
        try {
            List<TagId> requested = submitted.stream()
                    .map(UUID::fromString)
                    .map(TagId::from)
                    .toList();
            tagAssignmentCommands.assign(new AssignContentTagsCommand(
                    actors.resolve(authentication), id, requested, Instant.now()));
            return "redirect:/admin/content/" + contentId + "/edit?tagsSaved";
        } catch (ContentNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        } catch (ContentTagAssignmentRejectedException exception) {
            return renderTagAssignmentError(authentication, csrfToken, id, selected, exception.getMessage(), model);
        } catch (IllegalArgumentException exception) {
            return renderTagAssignmentError(
                    authentication, csrfToken, id, selected, "One or more requested tags are invalid.", model);
        }
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
        } catch (ContentCommandRejectedException exception) {
            try {
                var result = queries.getContentForAdmin(actors.resolve(authentication), id);
                var statusAwarePage = withTagAssignment(
                        views.edit(chrome.create(authentication, csrfToken), result, mapper.from(result), null),
                        authentication,
                        id,
                        Set.of(),
                        List.of(),
                        null);
                model.addAttribute(
                        "page", views.withErrors(statusAwarePage, List.of(), List.of(UPDATE_FAILED)));
            } catch (ContentNotFoundException missing) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
            }
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

    private String renderTagAssignmentError(
            Authentication authentication,
            CsrfToken csrfToken,
            ContentId id,
            Set<String> selected,
            String error,
            Model model) {
        var result = queries.getContentForAdmin(actors.resolve(authentication), id);
        var page = views.edit(chrome.create(authentication, csrfToken), result, mapper.from(result), null);
        model.addAttribute("page", withTagAssignment(page, authentication, id, selected, List.of(error), null));
        return "admin/content/form";
    }

    private AdminContentFormPage withTagAssignment(
            AdminContentFormPage page,
            Authentication authentication,
            ContentId id,
            Set<String> selected,
            List<String> errors,
            String successMessage) {
        var assignment = tagAssignments.view(actors.resolve(authentication), id);
        Set<String> effectiveSelection = selected.isEmpty() && errors.isEmpty()
                ? assignment.assignedTags().stream()
                        .map(tag -> tag.id().value().toString())
                        .collect(java.util.stream.Collectors.toSet())
                : selected;
        return views.withTagAssignment(page, assignment, effectiveSelection, errors, successMessage);
    }
}
