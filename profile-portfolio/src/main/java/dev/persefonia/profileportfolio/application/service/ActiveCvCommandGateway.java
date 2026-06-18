package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.command.ActiveCvUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpdateActiveCvCommand;

public interface ActiveCvCommandGateway {
    ActiveCvUpdateResult update(UpdateActiveCvCommand command);
}
