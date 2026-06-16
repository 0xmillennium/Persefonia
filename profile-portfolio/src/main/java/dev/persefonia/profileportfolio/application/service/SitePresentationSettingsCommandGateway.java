package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.command.SitePresentationSettingsUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpdateSitePresentationSettingsCommand;

public interface SitePresentationSettingsCommandGateway {
    SitePresentationSettingsUpdateResult update(UpdateSitePresentationSettingsCommand command);
}
