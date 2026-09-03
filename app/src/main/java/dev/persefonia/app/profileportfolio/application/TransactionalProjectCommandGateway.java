package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.app.audit.integration.ProfilePortfolioAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.profileportfolio.application.command.CreateProjectCommand;
import dev.persefonia.profileportfolio.application.command.ProjectMutationResult;
import dev.persefonia.profileportfolio.application.command.UpdateProjectCommand;
import dev.persefonia.profileportfolio.application.service.ProjectCommandGateway;
import dev.persefonia.profileportfolio.application.service.ProjectCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalProjectCommandGateway implements ProjectCommandGateway {
    private final ProjectCommandService service;
    private final AppendAuditRecordPort audit;
    private final ProfilePortfolioAuditMapper auditMapper;

    public TransactionalProjectCommandGateway(
            ProjectCommandService service,
            AppendAuditRecordPort audit,
            ProfilePortfolioAuditMapper auditMapper) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
    }

    @Override
    @Transactional
    public ProjectMutationResult create(CreateProjectCommand command) {
        ProjectMutationResult result = service.create(command);
        audit.append(auditMapper.projectCreated(command, result));
        return result;
    }

    @Override
    @Transactional
    public ProjectMutationResult update(UpdateProjectCommand command) {
        ProjectMutationResult result = service.update(command);
        audit.append(auditMapper.projectUpdated(command, result));
        return result;
    }
}
