package dev.persefonia.webadmin.contact;

import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommand;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommandService;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusResult;
import dev.persefonia.communication.application.query.ContactMessageAdminDetail;
import dev.persefonia.communication.application.query.ContactMessageAdminListRequest;
import dev.persefonia.communication.application.query.ContactMessageAdminQueryService;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public final class AdminContactController {
    private static final List<String> STATUS_OPTIONS = List.of(
            "all",
            ContactMessageStatus.NEW.name(),
            ContactMessageStatus.READ.name(),
            ContactMessageStatus.REPLIED.name(),
            ContactMessageStatus.SPAM.name(),
            ContactMessageStatus.ARCHIVED.name());

    private final ContactMessageAdminQueryService queries;
    private final UpdateContactMessageStatusCommandService commands;
    private final ContactMessageAdminActorResolver actors;
    private final AdminContactPageChromeFactory chrome;
    private final Clock clock;

    public AdminContactController(
            ContactMessageAdminQueryService queries,
            UpdateContactMessageStatusCommandService commands,
            ContactMessageAdminActorResolver actors,
            AdminContactPageChromeFactory chrome,
            Clock clock) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @GetMapping("/admin/contact")
    public String list(
            Authentication authentication,
            CsrfToken csrfToken,
            @RequestParam(name = "status", defaultValue = "all") String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", required = false) Integer pageSize,
            Model model) {
        String normalizedStatus = normalizeStatus(status);
        var request = new ContactMessageAdminListRequest(
                statusFilter(normalizedStatus),
                Math.max(1, page),
                boundedPageSize(pageSize));
        model.addAttribute("page", new AdminContactListPage(
                chrome.create(authentication, csrfToken),
                queries.list(request),
                normalizedStatus,
                STATUS_OPTIONS));
        return "admin/contact/list";
    }

    @GetMapping("/admin/contact/{messageId}")
    public String detail(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("messageId") String messageId,
            @RequestParam(name = "updated", required = false) String updated,
            @RequestParam(name = "rejected", required = false) String rejected,
            Model model) {
        ContactMessageAdminDetail detail = detail(parse(messageId));
        model.addAttribute("page", detailPage(authentication, csrfToken, detail, updated, rejected));
        return "admin/contact/detail";
    }

    @PostMapping("/admin/contact/{messageId}/read")
    public String markRead(Authentication authentication, @PathVariable("messageId") String messageId) {
        return update(authentication, messageId, ContactMessageStatus.READ);
    }

    @PostMapping("/admin/contact/{messageId}/replied")
    public String markReplied(Authentication authentication, @PathVariable("messageId") String messageId) {
        return update(authentication, messageId, ContactMessageStatus.REPLIED);
    }

    @PostMapping("/admin/contact/{messageId}/spam")
    public String markSpam(Authentication authentication, @PathVariable("messageId") String messageId) {
        return update(authentication, messageId, ContactMessageStatus.SPAM);
    }

    @PostMapping("/admin/contact/{messageId}/archive")
    public String archive(Authentication authentication, @PathVariable("messageId") String messageId) {
        return update(authentication, messageId, ContactMessageStatus.ARCHIVED);
    }

    private String update(Authentication authentication, String rawMessageId, ContactMessageStatus status) {
        ContactMessageId messageId = parse(rawMessageId);
        Instant changedAt = clock.instant();
        var result = commands.update(new UpdateContactMessageStatusCommand(
                actors.resolve(authentication),
                messageId,
                status,
                actors.changedBy(authentication),
                changedAt));
        if (result instanceof UpdateContactMessageStatusResult.Updated) {
            return "redirect:/admin/contact/" + messageId.value() + "?updated";
        }
        if (result instanceof UpdateContactMessageStatusResult.NotFound) {
            throw notFound();
        }
        return "redirect:/admin/contact/" + messageId.value() + "?rejected";
    }

    private AdminContactDetailPage detailPage(
            Authentication authentication,
            CsrfToken csrfToken,
            ContactMessageAdminDetail detail,
            String updated,
            String rejected) {
        return new AdminContactDetailPage(
                chrome.create(authentication, csrfToken),
                detail,
                AdminContactStatusAction.forMessage(detail.id(), detail.status()),
                updated == null ? null : "Contact message status updated.",
                rejected == null ? null : "Contact message status was not changed.");
    }

    private ContactMessageAdminDetail detail(ContactMessageId id) {
        return queries.findDetail(id).orElseThrow(AdminContactController::notFound);
    }

    private static ContactMessageId parse(String value) {
        try {
            return ContactMessageId.from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    private static String normalizeStatus(String status) {
        String value = status == null || status.isBlank() ? "all" : status.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(value)) {
            return "all";
        }
        for (ContactMessageStatus candidate : ContactMessageStatus.values()) {
            if (candidate.name().equals(value)) {
                return value;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported contact message status filter.");
    }

    private static ContactMessageStatus statusFilter(String status) {
        return "all".equals(status) ? null : ContactMessageStatus.valueOf(status);
    }

    private static int boundedPageSize(Integer pageSize) {
        if (pageSize == null) {
            return ContactMessageAdminListRequest.DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(ContactMessageAdminListRequest.MAX_PAGE_SIZE, pageSize));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact message was not found.");
    }
}
