package dev.persefonia.webadmin.series;

import dev.persefonia.contentpublishing.application.command.AddSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.ArchiveSeriesCommand;
import dev.persefonia.contentpublishing.application.command.CreateSeriesCommand;
import dev.persefonia.contentpublishing.application.command.RemoveSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.ReorderSeriesEntriesCommand;
import dev.persefonia.contentpublishing.application.command.UpdateSeriesCommand;
import dev.persefonia.contentpublishing.application.exception.ContentNotFoundException;
import dev.persefonia.contentpublishing.application.exception.SeriesCommandRejectedException;
import dev.persefonia.contentpublishing.application.exception.SeriesNotFoundException;
import dev.persefonia.contentpublishing.application.query.SeriesEditView;
import dev.persefonia.contentpublishing.application.service.SeriesAdminQueryService;
import dev.persefonia.contentpublishing.application.service.SeriesCommandGateway;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntryId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesValidationException;
import dev.persefonia.webadmin.content.ContentAdminActorResolver;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
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
public final class AdminSeriesController {
    private static final String NOT_FOUND = "Series was not found.";

    private final SeriesCommandGateway commands;
    private final SeriesAdminQueryService queries;
    private final ContentAdminActorResolver actors;
    private final AdminSeriesPageChromeFactory chrome;

    public AdminSeriesController(
            SeriesCommandGateway commands,
            SeriesAdminQueryService queries,
            ContentAdminActorResolver actors,
            AdminSeriesPageChromeFactory chrome) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
    }

    @GetMapping("/admin/series")
    public String list(
            Authentication authentication,
            CsrfToken csrfToken,
            @RequestParam(name = "created", required = false) String created,
            @RequestParam(name = "saved", required = false) String saved,
            @RequestParam(name = "archived", required = false) String archived,
            Model model) {
        model.addAttribute("page", new AdminSeriesListPage(
                chrome.create(authentication, csrfToken),
                queries.list(actors.resolve(authentication)),
                successMessage(created, saved, archived)));
        return "admin/series/list";
    }

    @GetMapping("/admin/series/new")
    public String newForm(Authentication authentication, CsrfToken csrfToken, Model model) {
        model.addAttribute("page", createFormPage(
                chrome.create(authentication, csrfToken), new AdminSeriesForm(), List.of(), List.of()));
        return "admin/series/new";
    }

    @PostMapping("/admin/series")
    public String create(
            Authentication authentication,
            CsrfToken csrfToken,
            @ModelAttribute AdminSeriesForm form,
            Model model) {
        try {
            var result = commands.create(new CreateSeriesCommand(
                    actors.resolve(authentication),
                    ContentLanguage.valueOf(form.getLanguage()),
                    form.getTitle(),
                    form.getSlug(),
                    form.getDescription(),
                    Instant.now()));
            return "redirect:/admin/series/" + result.seriesId().value() + "/edit?created";
        } catch (SeriesCommandRejectedException exception) {
            model.addAttribute("page", createFormPage(
                    chrome.create(authentication, csrfToken), form, commandError(exception), List.of()));
        } catch (IllegalArgumentException | SeriesValidationException exception) {
            model.addAttribute("page", createFormPage(
                    chrome.create(authentication, csrfToken), form, validationError(form), List.of()));
        }
        return "admin/series/new";
    }

    @GetMapping("/admin/series/{seriesId}/edit")
    public String edit(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("seriesId") String seriesId,
            @RequestParam(name = "created", required = false) String created,
            @RequestParam(name = "saved", required = false) String saved,
            @RequestParam(name = "entryAdded", required = false) String entryAdded,
            @RequestParam(name = "entryRemoved", required = false) String entryRemoved,
            @RequestParam(name = "reordered", required = false) String reordered,
            Model model) {
        SeriesEditView series = series(authentication, seriesId);
        model.addAttribute("page", editFormPage(
                chrome.create(authentication, csrfToken),
                form(series),
                series,
                List.of(),
                List.of(),
                editSuccessMessage(created, saved, entryAdded, entryRemoved, reordered)));
        return "admin/series/edit";
    }

    @PostMapping("/admin/series/{seriesId}")
    public String update(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("seriesId") String seriesId,
            @ModelAttribute AdminSeriesForm form,
            Model model) {
        SeriesId id = parseSeriesId(seriesId);
        try {
            commands.update(new UpdateSeriesCommand(
                    actors.resolve(authentication), id, form.getTitle(), form.getSlug(), form.getDescription(), Instant.now()));
            return "redirect:/admin/series/" + seriesId + "/edit?saved";
        } catch (SeriesNotFoundException exception) {
            throw notFound();
        } catch (SeriesCommandRejectedException exception) {
            SeriesEditView series = series(authentication, seriesId);
            model.addAttribute("page", editFormPage(
                    chrome.create(authentication, csrfToken), form, series, commandError(exception), List.of(), null));
        } catch (IllegalArgumentException | SeriesValidationException exception) {
            SeriesEditView series = series(authentication, seriesId);
            model.addAttribute("page", editFormPage(
                    chrome.create(authentication, csrfToken), form, series, validationError(form), List.of(), null));
        }
        return "admin/series/edit";
    }

    @PostMapping("/admin/series/{seriesId}/archive")
    public String archive(Authentication authentication, @PathVariable("seriesId") String seriesId) {
        try {
            commands.archive(new ArchiveSeriesCommand(actors.resolve(authentication), parseSeriesId(seriesId), Instant.now()));
            return "redirect:/admin/series?archived";
        } catch (SeriesNotFoundException exception) {
            throw notFound();
        }
    }

    @PostMapping("/admin/series/{seriesId}/entries")
    public String addEntry(
            Authentication authentication,
            @PathVariable("seriesId") String seriesId,
            @RequestParam("contentItemId") String contentItemId) {
        try {
            commands.addEntry(new AddSeriesEntryCommand(
                    actors.resolve(authentication), parseSeriesId(seriesId), parseContentId(contentItemId), Instant.now()));
            return "redirect:/admin/series/" + seriesId + "/edit?entryAdded";
        } catch (ContentNotFoundException | SeriesNotFoundException exception) {
            throw notFound();
        } catch (SeriesCommandRejectedException exception) {
            return redirectError(seriesId, exception);
        }
    }

    @PostMapping("/admin/series/{seriesId}/entries/{entryId}/remove")
    public String removeEntry(
            Authentication authentication,
            @PathVariable("seriesId") String seriesId,
            @PathVariable("entryId") String entryId) {
        try {
            commands.removeEntry(new RemoveSeriesEntryCommand(
                    actors.resolve(authentication), parseSeriesId(seriesId), parseEntryId(entryId), Instant.now()));
            return "redirect:/admin/series/" + seriesId + "/edit?entryRemoved";
        } catch (SeriesNotFoundException exception) {
            throw notFound();
        } catch (SeriesCommandRejectedException exception) {
            return redirectError(seriesId, exception);
        }
    }

    @PostMapping("/admin/series/{seriesId}/entries/reorder")
    public String reorderEntries(
            Authentication authentication,
            @PathVariable("seriesId") String seriesId,
            @RequestParam(name = "orderedEntryIds", required = false) List<String> orderedEntryIds,
            @RequestParam(name = "entryIds", required = false) List<String> entryIds,
            @RequestParam(name = "positions", required = false) List<Integer> positions) {
        List<SeriesEntryId> ids = orderedIds(orderedEntryIds, entryIds, positions);
        try {
            commands.reorderEntries(new ReorderSeriesEntriesCommand(
                    actors.resolve(authentication), parseSeriesId(seriesId), ids, Instant.now()));
            return "redirect:/admin/series/" + seriesId + "/edit?reordered";
        } catch (SeriesNotFoundException exception) {
            throw notFound();
        } catch (SeriesCommandRejectedException exception) {
            return redirectError(seriesId, exception);
        }
    }

    private SeriesEditView series(Authentication authentication, String seriesId) {
        try {
            return queries.edit(actors.resolve(authentication), parseSeriesId(seriesId));
        } catch (SeriesNotFoundException exception) {
            throw notFound();
        }
    }

    private static AdminSeriesFormPage createFormPage(
            AdminSeriesPageChrome chrome,
            AdminSeriesForm form,
            List<AdminSeriesFieldError> fieldErrors,
            List<String> globalErrors) {
        return new AdminSeriesFormPage(
                chrome,
                form,
                "Create series",
                "/admin/series",
                null,
                null,
                fieldErrors,
                globalErrors,
                null);
    }

    private static AdminSeriesFormPage editFormPage(
            AdminSeriesPageChrome chrome,
            AdminSeriesForm form,
            SeriesEditView series,
            List<AdminSeriesFieldError> fieldErrors,
            List<String> globalErrors,
            String successMessage) {
        return new AdminSeriesFormPage(
                chrome,
                form,
                "Edit series",
                "/admin/series/" + series.id().value(),
                series,
                series.status().name().equals("ACTIVE") ? "/admin/series/" + series.id().value() + "/archive" : null,
                fieldErrors,
                globalErrors,
                successMessage);
    }

    private static AdminSeriesForm form(SeriesEditView series) {
        AdminSeriesForm form = new AdminSeriesForm();
        form.setLanguage(series.language().name());
        form.setTitle(series.title());
        form.setSlug(series.slug());
        form.setDescription(series.description().orElse(""));
        return form;
    }

    private static List<AdminSeriesFieldError> commandError(SeriesCommandRejectedException exception) {
        return switch (exception.reason()) {
            case DUPLICATE_SLUG -> List.of(new AdminSeriesFieldError("slug", "This slug is already in use for this language."));
            case DUPLICATE_ENTRY -> List.of(new AdminSeriesFieldError("contentItemId", "This content item is already in the series."));
            case LANGUAGE_MISMATCH -> List.of(new AdminSeriesFieldError("contentItemId", "Choose content in the same language as the series."));
            case ARCHIVED_CONTENT -> List.of(new AdminSeriesFieldError("contentItemId", "Archived content cannot be added."));
            case ARCHIVED_SERIES -> List.of(new AdminSeriesFieldError("series", "Archived series are read-only."));
            case ENTRY_NOT_FOUND -> List.of(new AdminSeriesFieldError("entry", "The entry does not exist in this series."));
            case INVALID_REORDER -> List.of(new AdminSeriesFieldError("entries", "The order must include every entry exactly once."));
        };
    }

    private static List<AdminSeriesFieldError> validationError(AdminSeriesForm form) {
        if (form.getTitle().isBlank()) {
            return List.of(new AdminSeriesFieldError("title", "This field is required."));
        }
        if (form.getSlug().isBlank()) {
            return List.of(new AdminSeriesFieldError("slug", "This field is required."));
        }
        return List.of(new AdminSeriesFieldError("series", "Provide a valid language, title, slug, and description."));
    }

    private static String redirectError(String seriesId, SeriesCommandRejectedException exception) {
        return "redirect:/admin/series/" + seriesId + "/edit?seriesError=" + exception.reason().name();
    }

    private static List<SeriesEntryId> orderedIds(
            List<String> orderedEntryIds,
            List<String> entryIds,
            List<Integer> positions) {
        if (orderedEntryIds != null) {
            return orderedEntryIds.stream().map(AdminSeriesController::parseEntryId).toList();
        }
        if (entryIds == null || positions == null || entryIds.size() != positions.size()) {
            return List.of();
        }
        if (new HashSet<>(positions).size() != positions.size()) {
            return List.of();
        }
        return java.util.stream.IntStream.range(0, entryIds.size())
                .mapToObj(index -> new EntryOrder(entryIds.get(index), positions.get(index)))
                .sorted(Comparator.comparingInt(EntryOrder::position))
                .map(entry -> parseEntryId(entry.entryId()))
                .toList();
    }

    private static SeriesId parseSeriesId(String value) {
        try {
            return SeriesId.from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    private static SeriesEntryId parseEntryId(String value) {
        try {
            return SeriesEntryId.from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    private static ContentId parseContentId(String value) {
        try {
            return ContentId.from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    private static String successMessage(String created, String saved, String archived) {
        if (created != null) return "Series created.";
        if (saved != null) return "Series saved.";
        return archived != null ? "Series archived." : null;
    }

    private static String editSuccessMessage(
            String created, String saved, String entryAdded, String entryRemoved, String reordered) {
        if (created != null) return "Series created.";
        if (saved != null) return "Series saved.";
        if (entryAdded != null) return "Entry added.";
        if (entryRemoved != null) return "Entry removed.";
        return reordered != null ? "Entries reordered." : null;
    }

    private record EntryOrder(String entryId, int position) {
    }
}
