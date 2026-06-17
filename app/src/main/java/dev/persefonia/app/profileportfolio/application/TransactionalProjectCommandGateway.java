package dev.persefonia.app.profileportfolio.application;

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

    public TransactionalProjectCommandGateway(ProjectCommandService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    @Transactional
    public ProjectMutationResult create(CreateProjectCommand command) {
        return service.create(command);
    }

    @Override
    @Transactional
    public ProjectMutationResult update(UpdateProjectCommand command) {
        return service.update(command);
    }
}
