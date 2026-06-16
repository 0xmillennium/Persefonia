package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.command.PersonalProfileUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpsertActivePersonalProfileCommand;

public interface PersonalProfileCommandGateway {
    PersonalProfileUpdateResult upsertActive(UpsertActivePersonalProfileCommand command);
}
