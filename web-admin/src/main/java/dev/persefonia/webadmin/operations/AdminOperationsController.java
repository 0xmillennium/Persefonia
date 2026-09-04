package dev.persefonia.webadmin.operations;

import dev.persefonia.platformoperations.application.operations.*;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationStatus;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public final class AdminOperationsController {
    private final CacheInvalidationOperationsQueryPort cacheQueries;
    private final OperationsHealthQueryPort healthQueries;
    private final CacheInvalidationRecoveryGateway recovery;
    private final AdminOperationsActorResolver actors;
    private final AdminOperationsPageChromeFactory chrome;
    private final Clock clock;

    public AdminOperationsController(
            CacheInvalidationOperationsQueryPort cacheQueries,
            OperationsHealthQueryPort healthQueries,
            CacheInvalidationRecoveryGateway recovery,
            AdminOperationsActorResolver actors,
            AdminOperationsPageChromeFactory chrome,
            Clock clock) {
        this.cacheQueries = Objects.requireNonNull(cacheQueries, "cacheQueries");
        this.healthQueries = Objects.requireNonNull(healthQueries, "healthQueries");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @GetMapping("/admin/operations")
    public String index(Authentication authentication, CsrfToken csrfToken,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "pageSize", required = false) String pageSize,
            Model model) {
        CacheInvalidationStatus parsedStatus = parseStatus(status);
        CacheInvalidationOperationsSearchRequest request = new CacheInvalidationOperationsSearchRequest(
                parsedStatus, parsePositive(page, 1), parsePageSize(pageSize));
        model.addAttribute("page", new AdminOperationsPage(
                chrome.create(authentication, csrfToken), healthQueries.snapshot(), cacheQueries.summarize(),
                cacheQueries.search(request), new CacheInvalidationStatusFilter(parsedStatus)));
        return "admin/operations/index";
    }

    @GetMapping("/admin/operations/cache/{batchId}")
    public String detail(Authentication authentication, CsrfToken csrfToken,
            @PathVariable("batchId") String batchId, Model model) {
        var detail = cacheQueries.findById(parseId(batchId)).orElseThrow(AdminOperationsController::notFound);
        model.addAttribute("page", new AdminOperationsDetailPage(chrome.create(authentication, csrfToken), detail));
        return "admin/operations/cache-detail";
    }

    @PostMapping("/admin/operations/cache/{batchId}/execute")
    public String execute(Authentication authentication, @PathVariable("batchId") String batchId) {
        return handle(recovery.requestInitialExecution(command(authentication, batchId)), batchId);
    }

    @PostMapping("/admin/operations/cache/{batchId}/retry")
    public String retry(Authentication authentication, @PathVariable("batchId") String batchId) {
        return handle(recovery.requestRetry(command(authentication, batchId)), batchId);
    }

    @PostMapping("/admin/operations/cache/{batchId}/resume")
    public String resume(Authentication authentication, @PathVariable("batchId") String batchId) {
        return handle(recovery.requestStrandedResume(command(authentication, batchId)), batchId);
    }

    private CacheInvalidationRecoveryCommand command(Authentication authentication, String batchId) {
        return new CacheInvalidationRecoveryCommand(actors.resolve(authentication), parseId(batchId), clock.instant());
    }

    private static String handle(CacheRecoveryCommandResult result, String batchId) {
        return switch (result) {
            case ACCEPTED -> "redirect:/admin/operations/cache/" + batchId;
            case NOT_FOUND -> throw notFound();
            case NOT_ELIGIBLE -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The requested cache recovery action is not currently eligible.");
        };
    }

    private static CacheInvalidationBatchId parseId(String value) {
        try { return CacheInvalidationBatchId.from(UUID.fromString(value)); }
        catch (RuntimeException exception) { throw notFound(); }
    }
    private static CacheInvalidationStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try { return CacheInvalidationStatus.valueOf(value.trim()); }
        catch (RuntimeException exception) { throw badRequest(); }
    }
    private static int parsePositive(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try { int parsed = Integer.parseInt(value.trim()); if (parsed < 1) throw badRequest(); return parsed; }
        catch (NumberFormatException exception) { throw badRequest(); }
    }
    private static int parsePageSize(String value) {
        if (value == null || value.isBlank()) return 25;
        int parsed = parsePositive(value, 25);
        if (parsed != 25 && parsed != 50 && parsed != 100) throw badRequest();
        return parsed;
    }
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cache invalidation batch was not found.");
    }
    private static ResponseStatusException badRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid operations filter.");
    }
}
