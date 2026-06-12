package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.command.ArchiveContentCommand;
import dev.persefonia.contentpublishing.application.command.PublishContentCommand;
import dev.persefonia.contentpublishing.application.command.UnpublishContentCommand;
import dev.persefonia.contentpublishing.application.exception.ContentApplicationException;
import dev.persefonia.contentpublishing.application.exception.ContentNotFoundException;
import dev.persefonia.contentpublishing.application.service.ContentCommandGateway;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public final class AdminContentLifecycleController {
    private static final String NOT_FOUND = "Content was not found.";

    private final ContentCommandGateway commands;
    private final ContentAdminActorResolver actors;

    public AdminContentLifecycleController(ContentCommandGateway commands, ContentAdminActorResolver actors) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.actors = Objects.requireNonNull(actors, "actors");
    }

    @PostMapping("/admin/content/{contentId}/publish")
    public String publish(Authentication authentication, @PathVariable("contentId") String contentId) {
        ContentId id = parseContentId(contentId);
        try {
            commands.publishContent(new PublishContentCommand(actors.resolve(authentication), id, Instant.now(), null));
            return editRedirect(contentId, "published");
        } catch (ContentNotFoundException exception) {
            throw notFound();
        } catch (ContentApplicationException | IllegalStateException exception) {
            return editRedirect(contentId, "publishFailed");
        }
    }

    @PostMapping("/admin/content/{contentId}/unpublish")
    public String unpublish(Authentication authentication, @PathVariable("contentId") String contentId) {
        ContentId id = parseContentId(contentId);
        try {
            commands.unpublishContent(new UnpublishContentCommand(actors.resolve(authentication), id, Instant.now()));
            return editRedirect(contentId, "unpublished");
        } catch (ContentNotFoundException exception) {
            throw notFound();
        } catch (ContentApplicationException | IllegalStateException exception) {
            return editRedirect(contentId, "unpublishFailed");
        }
    }

    @PostMapping("/admin/content/{contentId}/archive")
    public String archive(Authentication authentication, @PathVariable("contentId") String contentId) {
        ContentId id = parseContentId(contentId);
        try {
            commands.archiveContent(new ArchiveContentCommand(actors.resolve(authentication), id, Instant.now()));
            return "redirect:/admin/content?archived=true";
        } catch (ContentNotFoundException exception) {
            throw notFound();
        } catch (ContentApplicationException | IllegalStateException exception) {
            return editRedirect(contentId, "archiveFailed");
        }
    }

    private static String editRedirect(String contentId, String result) {
        return "redirect:/admin/content/" + contentId + "/edit?" + result + "=true";
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
}
