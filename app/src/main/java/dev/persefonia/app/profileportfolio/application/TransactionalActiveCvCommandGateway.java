package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.profileportfolio.application.command.ActiveCvUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpdateActiveCvCommand;
import dev.persefonia.profileportfolio.application.service.ActiveCvCommandGateway;
import dev.persefonia.profileportfolio.application.service.ActiveCvCommandService;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnBean(ActiveCvCommandService.class)
public class TransactionalActiveCvCommandGateway implements ActiveCvCommandGateway {
    private final ActiveCvCommandService service;

    public TransactionalActiveCvCommandGateway(ActiveCvCommandService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    @Transactional
    public ActiveCvUpdateResult update(UpdateActiveCvCommand command) {
        return service.update(command);
    }
}
