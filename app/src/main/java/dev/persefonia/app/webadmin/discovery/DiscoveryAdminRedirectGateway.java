package dev.persefonia.app.webadmin.discovery;

import dev.persefonia.discovery.application.authorization.AdminRedirectCommandActor;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.port.ListRedirectRulesPort;
import dev.persefonia.discovery.application.redirect.CreateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleListQuery;
import dev.persefonia.discovery.application.redirect.RedirectRuleSummary;
import dev.persefonia.discovery.application.service.AdminRedirectCommandGateway;
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
    private final ListRedirectRulesPort lists;
    private final AdminRedirectCommandGateway commands;

    public DiscoveryAdminRedirectGateway(
            ListRedirectRulesPort lists,
            AdminRedirectCommandGateway commands) {
        this.lists = Objects.requireNonNull(lists, "lists");
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    @Override
    public AdminRedirectListResult list() {
        return new AdminRedirectListResult(lists.list(RedirectRuleListQuery.latestAll()).rules().stream()
                .map(DiscoveryAdminRedirectGateway::rule)
                .toList());
    }

    @Override
    public AdminRedirectCreateResult create(AdminRedirectCommandActor actor, AdminRedirectForm form) {
        AdminRedirectCreateMapping mapping = map(form);
        if (mapping instanceof AdminRedirectCreateMapping.Rejected rejected) {
            return new AdminRedirectCreateResult.Rejected(rejected.fieldErrors(), List.of());
        }

        var result = commands.create(((AdminRedirectCreateMapping.Mapped) mapping).command(actor));
        return switch (result) {
            case RedirectRuleCreationResult.Created ignored -> new AdminRedirectCreateResult.Created();
            case RedirectRuleCreationResult.Noop ignored -> new AdminRedirectCreateResult.Noop();
            case RedirectRuleCreationResult.Rejected rejected -> new AdminRedirectCreateResult.Rejected(
                    List.of(rejectedCreateError(rejected.reason())),
                    List.of());
        };
    }

    @Override
    public AdminRedirectDeactivateResult deactivate(AdminRedirectCommandActor actor, String redirectRuleId) {
        RedirectRuleId id = parseRedirectRuleId(redirectRuleId);
        if (id == null) {
            return AdminRedirectDeactivateResult.NOT_FOUND;
        }
        return switch (commands.deactivate(new DeactivateManualRedirectCommand(actor, id))) {
            case DeactivateRedirectRuleResult.Deactivated ignored -> AdminRedirectDeactivateResult.DEACTIVATED;
            case DeactivateRedirectRuleResult.AlreadyInactive ignored -> AdminRedirectDeactivateResult.ALREADY_INACTIVE;
            case DeactivateRedirectRuleResult.NotFound ignored -> AdminRedirectDeactivateResult.NOT_FOUND;
        };
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

        return new AdminRedirectCreateMapping.Mapped(sourceUrl, targetUrl, statusCode);
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

        record Mapped(
                PublicUrl sourceUrl,
                PublicUrl targetUrl,
                RedirectStatusCode statusCode) implements AdminRedirectCreateMapping {
            private CreateManualRedirectCommand command(AdminRedirectCommandActor actor) {
                return new CreateManualRedirectCommand(actor, sourceUrl, targetUrl, statusCode);
            }
        }

        record Rejected(List<AdminRedirectFieldError> fieldErrors) implements AdminRedirectCreateMapping {
        }
    }
}
