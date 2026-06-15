package dev.persefonia.webadmin.translation;

import dev.persefonia.contentpublishing.application.command.AddTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.CreateTranslationGroupCommand;
import dev.persefonia.contentpublishing.application.command.RemoveTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.exception.ContentNotFoundException;
import dev.persefonia.contentpublishing.application.exception.TranslationGroupCommandRejectedException;
import dev.persefonia.contentpublishing.application.exception.TranslationGroupNotFoundException;
import dev.persefonia.contentpublishing.application.service.TranslationGroupCommandGateway;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntryId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import dev.persefonia.webadmin.content.ContentAdminActorResolver;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public final class AdminTranslationGroupController {
    private static final String NOT_FOUND = "The requested resource was not found.";

    private final TranslationGroupCommandGateway commands;
    private final ContentAdminActorResolver actors;

    public AdminTranslationGroupController(
            TranslationGroupCommandGateway commands,
            ContentAdminActorResolver actors) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.actors = Objects.requireNonNull(actors, "actors");
    }

    @PostMapping("/admin/content/{contentId}/translation-group")
    public String create(Authentication authentication, @PathVariable("contentId") String contentId) {
        ContentId id = parseContentId(contentId);
        try {
            commands.create(new CreateTranslationGroupCommand(actors.resolve(authentication), id, Instant.now()));
            return redirect(contentId, "translationGroupCreated");
        } catch (ContentNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        } catch (TranslationGroupCommandRejectedException exception) {
            return redirectError(contentId, exception);
        }
    }

    @PostMapping("/admin/translation-groups/{groupId}/entries")
    public String addEntry(
            Authentication authentication,
            @PathVariable("groupId") String groupId,
            @RequestParam("contentItemId") String contentItemId,
            @RequestParam("returnContentId") String returnContentId) {
        TranslationGroupId group = parseGroupId(groupId);
        ContentId candidate = parseContentId(contentItemId);
        parseContentId(returnContentId);
        try {
            commands.addEntry(new AddTranslationEntryCommand(
                    actors.resolve(authentication), group, candidate, Instant.now()));
            return redirect(returnContentId, "translationEntryAdded");
        } catch (ContentNotFoundException | TranslationGroupNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        } catch (TranslationGroupCommandRejectedException exception) {
            return redirectError(returnContentId, exception);
        }
    }

    @PostMapping("/admin/translation-groups/{groupId}/entries/{entryId}/remove")
    public String removeEntry(
            Authentication authentication,
            @PathVariable("groupId") String groupId,
            @PathVariable("entryId") String entryId,
            @RequestParam("returnContentId") String returnContentId) {
        TranslationGroupId group = parseGroupId(groupId);
        TranslationGroupEntryId entry = parseEntryId(entryId);
        parseContentId(returnContentId);
        try {
            commands.removeEntry(new RemoveTranslationEntryCommand(
                    actors.resolve(authentication), group, entry, Instant.now()));
            return redirect(returnContentId, "translationEntryRemoved");
        } catch (TranslationGroupNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        } catch (TranslationGroupCommandRejectedException exception) {
            return redirectError(returnContentId, exception);
        }
    }

    private static String redirect(String contentId, String flag) {
        return "redirect:/admin/content/" + contentId + "/edit?" + flag;
    }

    private static String redirectError(String contentId, TranslationGroupCommandRejectedException exception) {
        return "redirect:/admin/content/" + contentId + "/edit?translationError=" + exception.reason().name();
    }

    private static ContentId parseContentId(String value) {
        try {
            return ContentId.from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
    }

    private static TranslationGroupId parseGroupId(String value) {
        try {
            return TranslationGroupId.from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
    }

    private static TranslationGroupEntryId parseEntryId(String value) {
        try {
            return TranslationGroupEntryId.from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
    }
}
