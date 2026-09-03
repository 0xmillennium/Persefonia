package dev.persefonia.app.audit.integration;

import static dev.persefonia.app.audit.integration.AdminAuditCommandFactory.metadata;

import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.identityaccess.application.admin.bootstrap.AdminBootstrapResult;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class IdentityAccessAuditMapper {
    private final AdminAuditCommandFactory factory;

    public IdentityAccessAuditMapper(AdminAuditCommandFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public AppendAuditRecordCommand bootstrapped(AdminBootstrapResult result) {
        return factory.bootstrap(
                result.account().id().value(),
                List.of(metadata("bootstrap_outcome", result.outcome())));
    }
}
