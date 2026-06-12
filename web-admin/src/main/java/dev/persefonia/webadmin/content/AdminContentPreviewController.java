package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.command.PreviewContentCommand;
import dev.persefonia.contentpublishing.application.exception.ContentCommandRejectedException;
import dev.persefonia.contentpublishing.application.exception.ContentNotFoundException;
import dev.persefonia.contentpublishing.application.service.ContentCommandGateway;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public final class AdminContentPreviewController {
    private final ContentCommandGateway commands;
    private final ContentAdminActorResolver actors;
    private final AdminContentPageChromeFactory chrome;
    private final AdminContentPreviewViewModelFactory views;

    public AdminContentPreviewController(
            ContentCommandGateway commands,
            ContentAdminActorResolver actors,
            AdminContentPageChromeFactory chrome,
            AdminContentPreviewViewModelFactory views) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
        this.views = Objects.requireNonNull(views, "views");
    }

    @GetMapping("/admin/content/{contentId}/preview")
    public String preview(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("contentId") String contentId,
            Model model) {
        var pageChrome = chrome.create(authentication, csrfToken);
        ContentId id = parseContentId(contentId);
        try {
            var result = commands.previewContent(
                    new PreviewContentCommand(actors.resolve(authentication), id, Instant.now()));
            model.addAttribute("page", views.success(pageChrome, result));
        } catch (ContentNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content was not found.");
        } catch (ContentCommandRejectedException exception) {
            model.addAttribute("page", views.error(pageChrome, contentId, "Preview requires saved Markdown source."));
        }
        return "admin/content/preview";
    }

    private static ContentId parseContentId(String value) {
        try {
            return ContentId.from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content was not found.");
        }
    }
}
