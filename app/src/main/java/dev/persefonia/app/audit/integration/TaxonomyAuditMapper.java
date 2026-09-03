package dev.persefonia.app.audit.integration;

import static dev.persefonia.app.audit.integration.AdminAuditCommandFactory.metadata;

import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.taxonomy.application.command.ArchiveTagCommand;
import dev.persefonia.taxonomy.application.command.CreateTagCommand;
import dev.persefonia.taxonomy.application.command.TagCommandResult;
import dev.persefonia.taxonomy.application.command.UpdateTagCommand;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class TaxonomyAuditMapper {
    private final AdminAuditCommandFactory factory;

    public TaxonomyAuditMapper(AdminAuditCommandFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public AppendAuditRecordCommand created(CreateTagCommand command, TagCommandResult result) {
        return map(AuditActionCatalog.TAG_CREATED, command.actor().identityRef(), result);
    }

    public AppendAuditRecordCommand updated(UpdateTagCommand command, TagCommandResult result) {
        return map(AuditActionCatalog.TAG_UPDATED, command.actor().identityRef(), result);
    }

    public AppendAuditRecordCommand archived(ArchiveTagCommand command, TagCommandResult result) {
        return map(AuditActionCatalog.TAG_ARCHIVED, command.actor().identityRef(), result);
    }

    private AppendAuditRecordCommand map(String action, java.util.UUID actorId, TagCommandResult result) {
        return factory.admin(
                action,
                actorId,
                AuditEntityCatalog.TAG,
                result.tagId().value(),
                List.of(),
                List.of(metadata("status", result.status())));
    }
}
