package dev.persefonia.app.audit.integration;

import static dev.persefonia.app.audit.integration.AdminAuditCommandFactory.metadata;

import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.discovery.application.redirect.CreateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.RedirectRuleChangeSummary;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class DiscoveryAuditMapper {
    private final AdminAuditCommandFactory factory;

    public DiscoveryAuditMapper(AdminAuditCommandFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public AppendAuditRecordCommand created(
            CreateManualRedirectCommand command, RedirectRuleChangeSummary result) {
        return map(AuditActionCatalog.REDIRECT_CREATED, command.actor().identityRef(), result);
    }

    public AppendAuditRecordCommand deactivated(
            DeactivateManualRedirectCommand command, RedirectRuleChangeSummary result) {
        return map(AuditActionCatalog.REDIRECT_DEACTIVATED, command.actor().identityRef(), result);
    }

    private AppendAuditRecordCommand map(
            String action, java.util.UUID actorId, RedirectRuleChangeSummary result) {
        return factory.admin(
                action,
                actorId,
                AuditEntityCatalog.REDIRECT_RULE,
                result.redirectRuleId().value(),
                List.of(),
                List.of(
                        metadata("source_path", result.sourceUrl().value()),
                        metadata("target_path", result.targetUrl().value()),
                        metadata("status_code", result.statusCode().value()),
                        metadata("reason", result.reason())));
    }
}
