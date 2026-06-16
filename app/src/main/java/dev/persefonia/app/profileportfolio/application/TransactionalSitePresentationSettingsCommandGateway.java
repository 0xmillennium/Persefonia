package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.profileportfolio.application.command.SitePresentationSettingsUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpdateSitePresentationSettingsCommand;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsCommandGateway;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalSitePresentationSettingsCommandGateway
        implements SitePresentationSettingsCommandGateway {
    private final SitePresentationSettingsCommandService service;

    public TransactionalSitePresentationSettingsCommandGateway(SitePresentationSettingsCommandService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    @Transactional
    public SitePresentationSettingsUpdateResult update(UpdateSitePresentationSettingsCommand command) {
        return service.update(command);
    }
}
