package dev.persefonia.app.webadmin.discovery;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.port.CreateRedirectRulePort;
import dev.persefonia.discovery.application.port.DeactivateRedirectRulePort;
import dev.persefonia.discovery.application.port.ListRedirectRulesPort;
import dev.persefonia.discovery.application.redirect.CreateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleListQuery;
import dev.persefonia.discovery.application.redirect.RedirectRuleSummary;
import dev.persefonia.discovery.domain.RedirectRuleId;
import dev.persefonia.webadmin.discovery.AdminRedirectCreateResult;
import dev.persefonia.webadmin.discovery.AdminRedirectDeactivateResult;
import dev.persefonia.webadmin.discovery.AdminRedirectFieldError;
import dev.persefonia.webadmin.discovery.AdminRedirectForm;
import dev.persefonia.webadmin.discovery.AdminRedirectGateway;
import dev.persefonia.webadmin.discovery.AdminRedirectListResult;
import dev.persefonia.webadmin.discovery.AdminRedirectRuleView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class DiscoveryAdminRedirectGateway implements AdminRedirectGateway {
    private static final String SAFE_CREATE_ERROR = "The redirect could not be created.";

    private final ListRedirectRulesPort lists;
    private final CreateRedirectRulePort creates;
    private final DeactivateRedirectRulePort deactivates;

    public DiscoveryAdminRedirectGateway(
            ListRedirectRulesPort lists,
            CreateRedirectRulePort creates,
            DeactivateRedirectRulePort deactivates) {
        this.lists = Objects.requireNonNull(lists, "lists");
        this.creates = Objects.requireNonNull(creates, "creates");
        this.deactivates = Objects.requireNonNull(deactivates, "deactivates");
    }

    @Override
    public AdminRedirectListResult list() {
        return new AdminRedirectListResult(lists.list(RedirectRuleListQuery.latestAll()).rules().stream()
                .map(DiscoveryAdminRedirectGateway::rule)
                .toList());
    }

    @Override
    public AdminRedirectCreateResult create(AdminRedirectForm form) {
        AdminRedirectCreateMapping mapping = map(form);
        if (mapping instanceof AdminRedirectCreateMapping.Rejected rejected) {
            return new AdminRedirectCreateResult.Rejected(rejected.fieldErrors(), List.of());
        }

        try {
            var result = creates.create(((AdminRedirectCreateMapping.Mapped) mapping).command());
            return switch (result) {
                case RedirectRuleCreationResult.Created ignored -> new AdminRedirectCreateResult.Created();
                case RedirectRuleCreationResult.Noop ignored -> new AdminRedirectCreateResult.Noop();
                case RedirectRuleCreationResult.Rejected rejected -> new AdminRedirectCreateResult.Rejected(
                        List.of(rejectedCreateError(rejected.reason())),
                        List.of());
            };
        } catch (RuntimeException exception) {
            return new AdminRedirectCreateResult.Rejected(List.of(), List.of(SAFE_CREATE_ERROR));
        }
    }

    @Override
    public AdminRedirectDeactivateResult deactivate(String redirectRuleId) {
        RedirectRuleId id = parseRedirectRuleId(redirectRuleId);
        if (id == null) {
            return AdminRedirectDeactivateResult.NOT_FOUND;
        }
        try {
            return switch (deactivates.deactivate(new DeactivateRedirectRuleCommand(id))) {
                case DeactivateRedirectRuleResult.Deactivated ignored -> AdminRedirectDeactivateResult.DEACTIVATED;
                case DeactivateRedirectRuleResult.AlreadyInactive ignored -> AdminRedirectDeactivateResult.ALREADY_INACTIVE;
                case DeactivateRedirectRuleResult.NotFound ignored -> AdminRedirectDeactivateResult.NOT_FOUND;
            };
        } catch (RuntimeException exception) {
            return AdminRedirectDeactivateResult.FAILED;
        }
    }

    private static AdminRedirectCreateMapping map(AdminRedirectForm form) {
        List<AdminRedirectFieldError> errors = new ArrayList<>();
        PublicUrl sourceUrl = publicUrl(form.getSourceUrl(), "sourceUrl", errors);
        PublicUrl targetUrl = publicUrl(form.getTargetUrl(), "targetUrl", errors);
        RedirectStatusCode statusCode = statusCode(form.getStatusCode(), errors);

        if (sourceUrl != null && sourceUrl.equals(targetUrl)) {
            errors.add(new AdminRedirectFieldError("targetUrl", "Target path must differ from source path."));
        }
        if (!errors.isEmpty()) {
            return new AdminRedirectCreateMapping.Rejected(errors);
        }

        return new AdminRedirectCreateMapping.Mapped(new CreateRedirectRuleCommand(
                sourceUrl,
                targetUrl,
                statusCode,
                RedirectReason.MANUAL,
                null,
                null,
                null));
    }

    private static PublicUrl publicUrl(
            String value,
            String field,
            List<AdminRedirectFieldError> errors) {
        try {
            return new PublicUrl(value == null ? "" : value);
        } catch (IllegalArgumentException exception) {
            errors.add(new AdminRedirectFieldError(field, "Use a path-only URL such as /tr/articles/example."));
            return null;
        }
    }

    private static RedirectStatusCode statusCode(String value, List<AdminRedirectFieldError> errors) {
        return switch (value == null ? "" : value.trim()) {
            case "301" -> RedirectStatusCode.MOVED_PERMANENTLY_301;
            case "302" -> RedirectStatusCode.FOUND_302;
            case "307" -> RedirectStatusCode.TEMPORARY_REDIRECT_307;
            case "308" -> RedirectStatusCode.PERMANENT_REDIRECT_308;
            default -> {
                errors.add(new AdminRedirectFieldError("statusCode", "Choose 301, 302, 307, or 308."));
                yield null;
            }
        };
    }

    private static AdminRedirectFieldError rejectedCreateError(RedirectRuleCreationResult.Reason reason) {
        return switch (reason) {
            case DUPLICATE_ACTIVE_SOURCE ->
                    new AdminRedirectFieldError("sourceUrl", "An active redirect already exists for that source path.");
            case LOOP_DETECTED ->
                    new AdminRedirectFieldError("targetUrl", "This redirect would create a direct loop.");
            case INVALID_INPUT, UNSUPPORTED_STATUS ->
                    new AdminRedirectFieldError("sourceUrl", "The redirect request is not valid.");
        };
    }

    private static AdminRedirectRuleView rule(RedirectRuleSummary rule) {
        String id = rule.id().value().toString();
        return new AdminRedirectRuleView(
                id,
                rule.sourceUrl().value(),
                rule.targetUrl().value(),
                Integer.toString(rule.statusCode().value()),
                rule.reason().name(),
                rule.active() ? "active" : "inactive",
                rule.active(),
                sourceRef(rule),
                rule.createdAt().toString(),
                rule.updatedAt().toString(),
                Long.toString(rule.version().value()),
                rule.active() ? "/admin/discovery/redirects/" + id + "/deactivate" : null);
    }

    private static String sourceRef(RedirectRuleSummary rule) {
        if (!rule.hasSourceRef()) {
            return "None";
        }
        return rule.sourceContext().name() + " / " + rule.sourceType().name()
                + " / " + rule.sourceEntityId().value();
    }

    private static RedirectRuleId parseRedirectRuleId(String value) {
        try {
            return new RedirectRuleId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private sealed interface AdminRedirectCreateMapping
            permits AdminRedirectCreateMapping.Mapped,
                    AdminRedirectCreateMapping.Rejected {

        record Mapped(CreateRedirectRuleCommand command) implements AdminRedirectCreateMapping {
        }

        record Rejected(List<AdminRedirectFieldError> fieldErrors) implements AdminRedirectCreateMapping {
        }
    }
}
