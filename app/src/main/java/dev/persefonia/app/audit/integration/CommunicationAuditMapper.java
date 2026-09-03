package dev.persefonia.app.audit.integration;

import static dev.persefonia.app.audit.integration.AdminAuditCommandFactory.change;

import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommand;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusResult;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class CommunicationAuditMapper {
    private final AdminAuditCommandFactory factory;

    public CommunicationAuditMapper(AdminAuditCommandFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public AppendAuditRecordCommand statusChanged(
            UpdateContactMessageStatusCommand command,
            UpdateContactMessageStatusResult.Updated result) {
        return factory.admin(
                AuditActionCatalog.CONTACT_MESSAGE_STATUS_CHANGED,
                command.actor().identityRef(),
                AuditEntityCatalog.CONTACT_MESSAGE,
                result.messageId().value(),
                List.of(change("status", result.previousStatus(), result.currentStatus())),
                List.of());
    }
}
