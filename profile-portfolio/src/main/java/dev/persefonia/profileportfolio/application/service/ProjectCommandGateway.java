package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.command.CreateProjectCommand;
import dev.persefonia.profileportfolio.application.command.ProjectMutationResult;
import dev.persefonia.profileportfolio.application.command.UpdateProjectCommand;

public interface ProjectCommandGateway {
    ProjectMutationResult create(CreateProjectCommand command);

    ProjectMutationResult update(UpdateProjectCommand command);
}
