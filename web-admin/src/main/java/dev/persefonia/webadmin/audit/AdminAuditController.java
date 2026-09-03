package dev.persefonia.webadmin.audit;

import dev.persefonia.audit.application.port.AuditQueryPort;
import dev.persefonia.audit.application.query.AuditRecordDetail;
import dev.persefonia.audit.application.query.AuditSearchRequest;
import dev.persefonia.audit.domain.record.AuditAction;
import dev.persefonia.audit.domain.record.AuditActorType;
import dev.persefonia.audit.domain.record.AuditRecordId;
import dev.persefonia.audit.domain.record.SourceContext;
import dev.persefonia.audit.domain.record.SourceEntityId;
import dev.persefonia.audit.domain.record.SourceType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Read-only OWNER-protected Audit history controller. */
@Controller
public final class AdminAuditController {
    private final AuditQueryPort queries;
    private final AdminAuditPageChromeFactory chrome;

    public AdminAuditController(AuditQueryPort queries, AdminAuditPageChromeFactory chrome) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
    }

    @GetMapping("/admin/audit")
    public String list(
            Authentication authentication,
            CsrfToken csrfToken,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "actorType", required = false) String actorType,
            @RequestParam(name = "actorId", required = false) String actorId,
            @RequestParam(name = "entityContext", required = false) String entityContext,
            @RequestParam(name = "entityType", required = false) String entityType,
            @RequestParam(name = "entityId", required = false) String entityId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "pageSize", required = false) String pageSize,
            Model model) {
        AuditSearchRequest request = parseRequest(
                action, actorType, actorId, entityContext, entityType, entityId, from, to, page, pageSize);
        model.addAttribute("page", new AdminAuditListPage(
                chrome.create(authentication, csrfToken),
                queries.search(request),
                new AdminAuditFilterView(request)));
        return "admin/audit/list";
    }

    @GetMapping("/admin/audit/{auditRecordId}")
    public String detail(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("auditRecordId") String auditRecordId,
            Model model) {
        AuditRecordDetail record = queries.findById(parseId(auditRecordId)).orElseThrow(AdminAuditController::notFound);
        model.addAttribute("page", new AdminAuditDetailPage(chrome.create(authentication, csrfToken), record));
        return "admin/audit/detail";
    }

    private static AuditSearchRequest parseRequest(
            String action, String actorType, String actorId,
            String entityContext, String entityType, String entityId,
            String from, String to, String page, String pageSize) {
        try {
            return new AuditSearchRequest(
                    absent(action) ? null : AuditAction.of(action.trim()),
                    absent(actorType) ? null : AuditActorType.valueOf(actorType.trim()),
                    absent(actorId) ? null : SourceEntityId.from(UUID.fromString(actorId.trim())),
                    absent(entityContext) ? null : SourceContext.of(entityContext.trim()),
                    absent(entityType) ? null : SourceType.of(entityType.trim()),
                    absent(entityId) ? null : SourceEntityId.from(UUID.fromString(entityId.trim())),
                    absent(from) ? null : Instant.parse(from.trim()),
                    absent(to) ? null : Instant.parse(to.trim()),
                    normalizePage(page),
                    normalizePageSize(pageSize));
        } catch (RuntimeException exception) {
            throw badRequest();
        }
    }

    private static int normalizePage(String value) {
        return absent(value) ? AuditSearchRequest.DEFAULT_PAGE : Math.max(1, Integer.parseInt(value.trim()));
    }

    private static int normalizePageSize(String value) {
        if (absent(value)) {
            return AuditSearchRequest.DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(AuditSearchRequest.MAX_PAGE_SIZE, Integer.parseInt(value.trim())));
    }

    private static AuditRecordId parseId(String value) {
        try {
            return AuditRecordId.from(UUID.fromString(value));
        } catch (RuntimeException exception) {
            throw notFound();
        }
    }

    private static boolean absent(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseStatusException badRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid audit search filters.");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit record was not found.");
    }
}
